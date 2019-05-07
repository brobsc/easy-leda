(ns easy-leda.client
  (:require [clj-http.client :as client]
            [clojure.data.xml :as xml]
            [clojure.zip :as z]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]))

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


(defn download [mat exc]
  (client/post download-url
   {:headers {:upgrade-insecure-requests 1
              :origin "http://150.165.85.29:81"
              :user-agent "easy_leda"
              :accept "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3"}
    :form-params {:id exc
                  :matricula mat}
    :as :stream}))

(defn dl->zip [out res]
  (let [path (str out
                  (get
                   (re-find #"filename=\"(R.*)-environment.zip\""
                            (get-in res [:headers :content-disposition])) 1)
                  ".zip")]
    (io/copy (get-in res [:body])
             (io/file path)
             )
    path))

(defn zip->entries [zip]
  (lazy-seq
   (when-let [entry (.getNextEntry zip)]
     (cons entry (zip->entries zip)))))

(defn entry->file [entry zip out]
  (when-not (.isDirectory entry)
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

(defn zip->files [out zip]
  (loop [entry (.getNextEntry zip)]
    (if entry
      (do (entry->file entry zip out)
          (recur (.getNextEntry zip)))
      out)))

(defn res->stream [res]
  (-> res
      (:body)
      (io/input-stream)
      (java.util.zip.ZipInputStream.)))

(defn dl->folder [out res]
  (->> res
       (res->stream)
       (zip->files (get-path out res))))

(defn get-exc-name [exc g1]
  (format "R%02d-%s" exc (if g1 "01" "02")))

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
  (let [pom-path (io/file path "pom.xml")
        pom (slurp pom-path)
        exc-name (get-exc-name exc g1)]
    (-> pom
        (clojure.string/replace #"<matricula>(.*)</matricula>" (str "<matricula>" mat "</matricula>"))
        (clojure.string/replace #"<roteiro>(.*)</roteiro>" (str "<roteiro>" exc-name "</roteiro>"))
        (io/copy pom-path))))

(defn get-exercise [exc {:keys [mat g1 path]}]
  (let [exc-name (get-exc-name exc g1)]
    (->> (download mat exc-name)
         (dl->folder path)
         (update-pom exc mat g1))))
