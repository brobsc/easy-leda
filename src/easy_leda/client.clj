(ns easy-leda.client
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as s])
  (:import (java.util.zip ZipInputStream ZipEntry)))

(def download-url "http://150.165.85.29:81/download")

(defn log [& msg]
  (println (str "[LOG]: " (apply str msg))))

(defn get-exc-data [mat exc]
  (log (str "Pegando conteudo do exercicio " exc))
  (client/post download-url
               {:headers {:upgrade-insecure-requests 1
                          :origin "http://150.165.85.29:81"
                          :user-agent "easy_leda"
                          :accept "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3"}
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
               (s/includes? exc "R")))))

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
      (.renameTo (io/file from) (io/file to)))
    (log "Diretorio " from " ja tem o nome correto"))
  to)

(defn new-dir [mat base path]
  (log "Procurando nome correto para " path)
  (->> (pom-exc path)
       (get-exc-data mat)
       (full-path base)))

(defn organize [{:keys [mat path]}]
  (let [new-dir* (partial new-dir mat path)
        get-from-to (juxt identity new-dir*)
        move!* (partial apply move!)]
    (->> (subdirs path)
         (map get-from-to)
         (map move!*)
         (mapv str))))
