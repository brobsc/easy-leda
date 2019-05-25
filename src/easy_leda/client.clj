(ns easy-leda.client
  (:require [org.httpkit.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.zip :as z]
            [net.cgrand.enlive-html :as h])
  (:import (java.util.zip ZipInputStream ZipEntry)))

(def download-url "http://150.165.85.29:81/download")
(def crono-url "http://150.165.85.29:81/cronograma")
(def crono-page
  (->> (client/get "http://150.165.85.29:81/cronograma")
       (:body)
       (h/html-snippet)))

(defn log [& msg]
  (println (str "[LOG]: " (apply str msg))))

(defn get-exc-data [mat exc]
  (log (str "Pegando conteudo do exercicio " exc))
  @(client/post download-url
               {:headers {"Upgrade-Insecure-Requests" 1
                          "Origin" "http://150.165.85.29:81"
                          "User-Agent" "easy_leda"
                          "Accept" "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3"}
                :form-params {:id exc
                              :matricula mat}
                :as :stream}))

(defmulti dir? (fn [arg] (class arg)))
(defmethod dir? java.io.File [file] (.isDirectory file))
(defmethod dir? java.util.zip.ZipEntry [entry] (.isDirectory entry))
(defmethod dir? String [arg] (.isDirectory (io/file arg)))

(defn zip-entry->file! [^ZipEntry entry zip out]
  (when-not (dir? entry)
    (log (str "Extraindo " (.getName entry)))
    (let [file (io/file out (.getName entry))
          buff-size 4096
          buffer (byte-array buff-size)
          folder (io/file (.getParent file))]
      (.mkdirs folder)
      (with-open [out-file (io/output-stream file)]
        (loop [size (.read zip buffer)]
          (when (> size 0)
            (.write out-file buffer 0 size)
            (recur (.read zip buffer))))))))

(defn res->filename [res]
  ((re-find #"filename=\"(R.*)-environment.zip\""
            (get-in res [:headers :content-disposition])) 1))

(defn full-path [out res]
  (->> (res->filename res)
       (str out)))

(defn stream->files! [out ^ZipInputStream zip-stream]
  (log "Preparando para extrair arquivos para " out "...")
  (loop [entry (.getNextEntry zip-stream)]
    (if entry
      (do (zip-entry->file! entry zip-stream out)
          (recur (.getNextEntry zip-stream)))
      out)))

(defn res->stream [res]
  (log "Convertendo resposta para zip...")
  (-> res
      (:body)
      (io/input-stream)
      (ZipInputStream.)))

(defn dl->folder! [out res]
  (log "Preparando arquivos...")
  (->> (res->stream res)
       (stream->files! (full-path out res))))

(defn exc-name [exc g1]
  (format "%s-%s" exc (if g1 "01" "02")))

(defn update-pom! [exc mat g1 path]
  (log "Atualizando arquivo pom...")
  (let [pom-path (io/file path "pom.xml")
        pom (slurp pom-path)
        exc-name* (exc-name exc g1)]
    (-> pom
        (s/replace #"<matricula>(.*)</matricula>" (str "<matricula>" mat "</matricula>"))
        (s/replace #"<roteiro>(.*)</roteiro>" (str "<roteiro>" exc-name* "</roteiro>"))
        (io/copy pom-path)))
  path)

(defn print-cmd [path]
  (log "Exercicio baixado com sucesso!")
  (log "Execute:")
  (println "$" "cd" path "&&" "mvn install -DskipTests"))

(defn get-exercise [exc {:keys [mat g1 path]}]
  (log "Iniciando download de exercicio...")
  (let [exc-name* (exc-name exc g1)]
    (->> (get-exc-data mat exc-name*)
         (dl->folder! path)
         (update-pom! exc mat g1)
         (print-cmd))))

(defn pom-exc [path]
  (->> (io/file path "pom.xml")
       (slurp)
       (re-find #"<roteiro>(.*)</roteiro>")
       (last)))

(defn valid-pom? [path]
  (boolean (when (.exists (io/file path "pom.xml"))
             (when-let [exc (pom-exc path)]
               exc))))

(defn files [^java.io.File arg]
  (.listFiles arg))

(defn subdirs [path]
  (log "Localizando diretorios de LEDA...")
  (->> (io/file path)
       (files)
       (filter dir?)
       (filter valid-pom?)
       (mapv str)))

(defn move! [from to]
  (if-not (= from to)
    (do
      (log "Movendo " from " -> " to)
      #_(.renameTo (io/file from) (io/file to)))
    (log "Diretorio " from " ja tem o nome correto"))
  to)

#_(defn get-exc-name [exc]
  (let [shortname ((re-find #"(.*)-\d{2}" exc) 1)]
    (->> (h/select crono-page [:td :a])
         (filter (fn [td] (= (get-in td [:attrs :href]) (str "requestDownload?id=" shortname "-01"))))
         (first)
         (:content)
         (first))))

(defn get-exc-name [exc]
  (->> (h/select crono-page [:td :a])
       (filter #(= (get-in % [:attrs :href]) (str "requestDownload?id=" exc)))
       first
       h/text))

(defn deaccent [arg]
  "Remove accent from string"
  ;; http://www.matt-reid.co.uk/blog_post.php?id=69
  (let [normalized (java.text.Normalizer/normalize arg java.text.Normalizer$Form/NFD)]
    (s/replace normalized #"\p{InCombiningDiacriticalMarks}+" "")))

(defn capitalize-and-camel-case [arg]
  (->> (s/split arg #"\s")
       (map s/capitalize)
       (s/join)))

(defn formatted-name [arg]
  (as-> (deaccent arg) result
    (s/replace result #"[():,]" "")
    (capitalize-and-camel-case result)))

(defn new-dir [base path]
  (log "Procurando nome correto para " path)
  (let [exc (pom-exc path)]
    (log "Exercicio: " exc)
    (->> (get-exc-name exc)
         (formatted-name)
         (str exc "-")
         (str base))))

(defn organize [{:keys [path]}]
  (let [new-dir* (partial new-dir path)
        get-from-to (juxt identity new-dir*)
        move!* (partial apply move!)]
    (->> (subdirs path)
         (map get-from-to)
         (map move!*)
         (mapv str))))
