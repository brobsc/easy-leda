(ns easy-leda.client
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as s])
  (:import (java.util.zip ZipInputStream ZipEntry)))

;; https://stackoverflow.com/questions/5419125/reading-a-zip-file-using-java-api-from-clojure

(def download-url "http://150.165.85.29:81/download")

;; curl --header "Origin: http://150.165.85.29:81"
;; --header "Upgrade-Insecure-Requests: 1"
;; --header "Content-Type: application/x-www-form-urlencoded"
;; --header "User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36"
;; --header "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3"
;; -X POST
;; -d "id=R10-01&matricula=118110035"
;; -o result.zip
;; http://150.165.85.29:81/download

(defn log [& msg]
  (println (str "[" "LOG" "]: " (apply str msg))))

(defn download [mat exc]
  (log (str "Baixando exercicio " exc))
  (client/post download-url
   {:headers {:upgrade-insecure-requests 1
              :origin "http://150.165.85.29:81"
              :user-agent "easy_leda"
              :accept "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3"}
    :form-params {:id exc
                  :matricula mat}
    :as :stream}))

(defn zip-entry->file! [^ZipEntry entry zip out]
  (when-not (.isDirectory entry)
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

(defn get-path [out res]
  (str out
       (get
        (re-find #"filename=\"(R.*)-environment.zip\""
                 (get-in res [:headers :content-disposition])) 1)))

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

(defn dl->folder [out res]
  (log "Preparando arquivos...")
  (->> res
       (res->stream)
       (stream->files! (get-path out res))))

(defn get-exc-name [exc g1]
  (format "%s-%s" exc (if g1 "01" "02")))

;; Updating with xml parsing is far too much work for this simple task
#_(defn update-pom [exc mat g1 path]
  (let [pom-file (io/file path "pom.xml")
        current-pom (z/xml-zip (xml/parse (io/input-stream pom-file)))]
    (-> current-pom
        (zx/xml1-> :build :plugins :plugin :executions :execution :configuration :matricula)
        (z/edit #(assoc-in % [:content] mat))
        (z/up)
        (zx/xml1-> :roteiro)
        (z/edit #(assoc-in % [:content] exc))
        (z/root)
        (xml/indent-str))))

;; (xml/emit-str (z/root (z/edit (zx/xml1-> (z/up (z/edit (zx/xml1-> pomzip :build :plugins :plugin :executions :execution :configuration :matricula) #(assoc-in % [:content] "EITA"))) :roteiro) #(assoc-in % [:content] "ROT"))))

;; TODO: Find better way to replace a regex match
(defn update-pom [exc mat g1 path]
  (log "Atualizando arquivo pom...")
  (let [pom-path (io/file path "pom.xml")
        pom (slurp pom-path)
        exc-name (get-exc-name exc g1)]
    (-> pom
        (s/replace #"<matricula>(.*)</matricula>" (str "<matricula>" mat "</matricula>"))
        (s/replace #"<roteiro>(.*)</roteiro>" (str "<roteiro>" exc-name "</roteiro>"))
        (io/copy pom-path))))

(defn get-exercise [exc {:keys [mat g1 path]}]
  (log "Iniciando download de exercicio...")
  (let [exc-name (get-exc-name exc g1)]
    (->> (download mat exc-name)
         (dl->folder path)
         (update-pom exc mat g1)))
  (log "Exercicio baixado com sucesso!"))
