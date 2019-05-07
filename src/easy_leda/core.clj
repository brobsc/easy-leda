(ns easy-leda.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]))

(def user-file-path [(System/getProperty "user.home") ".easy_leda"])
(def user-file (apply io/file user-file-path))
(defn user-conf [] (read-string (slurp (doto (apply io/file user-file-path)))))

(defn prepare-user-file []
  (when-not (.exists user-file)
    (spit user-file "")))

(defn update-conf [mat]
  (spit user-file (prn-str {:mat mat})))

(defn read-conf []
  (print "Insira sua matricula: ")
  (flush)
  (let [mat (read-line)]
    (update-conf mat)))

(defn start []
  (prepare-user-file)
  (println user-conf))

(defn -main []
  (println "Hello World!")
  (read-conf))
