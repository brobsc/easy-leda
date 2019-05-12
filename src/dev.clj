(ns dev
  (:require [easy-leda.core]
            [nrepl.cmdline :as nrepl]
            [reply.main :as reply]))

(defn -main []
  (println "Dev started...")
  (reply/launch-nrepl {:port 6688})
  (shutdown-agents))
