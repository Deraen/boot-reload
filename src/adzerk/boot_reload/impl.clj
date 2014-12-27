(ns adzerk.boot-reload.impl
  (:require
    [boot.util :as util]
    [figwheel-sidecar.core :as fw]))

(def state (atom nil))

(defn start! [opts]
  ; FIXME: broken. opts should contain :output-to etc.
  (reset! state (fw/start-server opts)))

(defn send-css-files! [files]
  ; FIXME: files is just a vector of paths
  (fw/send-message! @state :css-files-changed {:files files})
  (doseq [f files]
    (util/info "Sending changed CSS file:" (:file f))))

(defn send-files-changed [files]
  ; FIXME: files is just a vector of paths
  (fw/send-message! @state :files-changed {:files files})
  (doseq [f files]
    (util/info "Notifying browser that file changed: %s" (:file f))))
