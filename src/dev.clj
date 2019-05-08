(ns dev
  (:require [easy-leda.core :as e]
            [nrepl.cmdline :as nrepl])
  (:gen-class))

(defn -main []
  (println "Dev started...")
  (nrepl/-main "-i"))
