(ns easy-leda.uberjar
  (:require [cambada.uberjar :as u]
            [cambada.jar :as jar]
            [cambada.cli :as ccli]
            [clojure.java.io :as io]
            [easy-leda.core :as e]))

(defn -main []
  (println "Buildando versao" e/-version)
  (let [task (ccli/args->task ["--app-version" e/-version
                               "-m" "easy_leda.core"]
                              jar/cli-options)]
    (u/apply! task))
  (println "Movendo jar")
  (-> (io/file (System/getProperty "user.dir")
               "target/"
               (str "easy-leda-"
               e/-version
               "-standalone.jar"))
      (io/copy (io/file (System/getProperty "user.dir") "easy-leda.jar"))))

