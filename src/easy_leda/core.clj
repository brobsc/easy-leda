(ns easy-leda.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [easy-leda.client :as lc]))

(def user-file-path [(System/getProperty "user.home") ".easy_leda"])
(def user-file (apply io/file user-file-path))
(defn user-conf [] (read-string (slurp (doto (apply io/file user-file-path)))))

(defn prepare-user-file []
  (when-not (.exists user-file)
    (spit user-file "{}")))

(defn update-conf [mat g1? path]
  (spit user-file (prn-str {:mat mat
                            :g1 g1?
                            :path path})))

(defn read-input [prompt]
  (print prompt)
  (flush)
  (let [inp (read-line)]
    inp))

(defn read-conf []
  (let [mat (read-input "Insira sua matricula: ")
        group (read-input "Turma 01? (s/n): ")
        path (read-input "Insira o diretorio dos roteiros: ")]
    (update-conf mat
                 (= "s" (clojure.string/lower-case group))
                 (clojure.string/join "/" [(System/getProperty "user.home") path ""]))))

(defn start []
  (prepare-user-file)
  (when-not (:mat (user-conf))
    (println "=== INICIANDO CONFIG ===")
    (read-conf)))

(defn get-exercise [exc]
  (lc/get-exercise exc (user-conf)))

(defn print-banner []
  (println "=================")
  (println "=== EASY LEDA ===")
  (println "================="))

(defn i-mode []
  (let [inp (read-input "Insira um comando (dl/conf): ")]
    (when inp
      (condp = (clojure.string/lower-case inp)
        "dl" (get-exercise (read-input "Insira o id do roteiro (RXX/PPX/AXX/RRX): "))
        "conf" (read-conf)
        (println "o/")))))

(defn args-mode [[cmd exc & _]]
  (condp = cmd
    "dl" (if exc (get-exercise exc) (println "Necessario um id de roteiro para baixar."))
    "conf" (read-conf)
    (println "comando " (str "\"" cmd "\"") "nao reconhecido.")))

(defn -main [& args]
  (start)
  (print-banner)
  (if args (args-mode args) (i-mode)))
