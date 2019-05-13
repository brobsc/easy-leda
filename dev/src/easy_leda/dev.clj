(ns easy-leda.dev
  (:require [easy-leda.core]
            [reply.main :as reply]))

(defn -main []
  (println "Dev started...")
  (reply/launch-nrepl {:port 6688})
  (shutdown-agents))
