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

(def ^:private deps '[[figwheel-sidecar "0.2.0-SNAPSHOT"]
                      [figwheel "0.2.0-SNAPSHOT"]])

(defn- make-pod []
  (future (-> (get-env) (update-in [:dependencies] into deps) pod/make-pod)))

(defn- write-cljs! [f on-jsload]
  (->> (template
         ((ns adzerk.boot-reload
            (:require
             [figwheel.client :as client]
             ~@(when on-jsload [(symbol (namespace on-jsload))])))
          (client/start
            {:on-jsload #(~(or on-jsload '+))})))
    (map pr-str) (interpose "\n") (apply str) (spit f)))

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
    (write-cljs! out on-jsload)
    (comp
     (with-pre-wrap
       fileset
       (pod/with-call-in @pod
         (adzerk.boot-reload.impl/start! {}))
       (-> fileset (add-resource tmp) commit!))
     (with-post-wrap
       fileset
       (let [changes (->> fileset
                          (fileset-diff @prev)
                          output-files)
             css-changes   (by-ext [".css"] changes)
             changed-files (not-by-ext [".css"] changes)]
         (pod/with-call-in @pod
           (do
             (adzerk.boot-reload.impl/send-css-files! ~@(map tmppath css-changes))
             (adzerk.boot-reload.impl/send-files-changed! ~@(map tmppath changed-files)))))
       (reset! prev fileset)))))
