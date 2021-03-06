(ns adzerk.boot-reload
  {:boot/export-tasks true}
  (:require
   [clojure.java.io    :as io]
   [clojure.set        :as set]
   [boot.pod           :as pod]
   [boot.file          :as file]
   [boot.util          :refer :all]
   [boot.core          :refer :all]
   [boot.from.backtick :refer [template]]))

(def ^:private deps '[[http-kit "2.1.18"]])

(defn- make-pod []
  (future (-> (get-env) (update-in [:dependencies] into deps) pod/make-pod)))

(defn- changed [before after]
  (when before
    (->> (fileset-diff before after) output-files (map tmppath) set)))

(defn- start-server [pod {:keys [ip port] :as opts}]
  (let [{:keys [ip port]}
        (pod/with-call-in pod (adzerk.boot-reload.server/start ~opts))
        host (if-not (= ip "0.0.0.0") ip "localhost")]
    (with-let [url (format "ws://%s:%d" host port)]
      (info "<< started reload server on %s >>\n" url))))

(defn- write-cljs! [f url on-jsload]
  (->> (template
         ((ns adzerk.boot-reload
            (:require
             [adzerk.boot-reload.client :as client]
             ~@(when on-jsload [(symbol (namespace on-jsload))])))
          (when-not (client/alive?)
            (client/connect ~url
              {:on-jsload #(~(or on-jsload '+))}))))
    (map pr-str) (interpose "\n") (apply str) (spit f)))

(defn- send-changed! [pod changed]
  (pod/with-call-in pod
    (adzerk.boot-reload.server/send-changed! ~(get-env :target-path) ~changed)))

(deftask reload
  "Live reload of page resources in browser via websocket.

  The default configuration starts a websocket server on a random available
  port on localhost."

  [i ip ADDR       str "The (optional) IP address for the websocket server to listen on."
   p port PORT     int "The (optional) port the websocket server listens on."
   j on-jsload SYM sym "The (optional) callback to call when JS files are reloaded."]

  (let [pod  (make-pod)
        tmp  (temp-dir!)
        prev (atom nil)
        out  (doto (io/file tmp "adzerk" "boot_reload.cljs") io/make-parents)]
    (write-cljs! out (start-server @pod {:ip ip :port port}) on-jsload)
    (comp
     (with-pre-wrap fileset
       (-> fileset (add-resource tmp) commit!))
     (with-post-wrap fileset
       (send-changed! @pod (changed @prev fileset))
       (reset! prev fileset)))))

