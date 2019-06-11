(ns easy-leda.uberjar
  (:require [cambada.uberjar :as u]
            [clojure.java.io :as io]
            [easy-leda.core :as e]))

(defn -main []
  (println "Buildando versao" e/-version)
  (u/-main "--app-version" e/-version
            "-m" "easy_leda.core")
  (println "Movendo jar")
  (-> (io/file (System/getProperty "user.dir")
                "target/"
                "easy-leda-"
                e/-version
                "-standalone.jar")
      (io/copy (io/file (System/getProperty "user.dir") "easy-leda.jar"))))

