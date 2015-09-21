(ns algebra.signature.monoid
  (:refer-clojure :exclude [name])
  (:require ;algebra.models.monoid
            [clojure.tools.logging :as log :only [debug info]]))

(clojure.core/println "loading algebra.signature.monoid")

;; constants
(def id (atom 0))

;; operators
(defprotocol Operators
  "Operator Signature for Monoids"
  (structure [t]) ;; returns Keyword
  (** [arg1 arg2] [t arg1 arg2])
  (constants [t]))

;; laws
(declare dispatch-type)

(defn closure
  [a b]
  (log/info "monoid law: associativity")
  (let [s (** a b)
        log (log/debug "type sum: " (type s))
        dt (dispatch-type)
        log (log/debug "dispatch type: " dt)]
    (if-let [t (= (class s) (class dt))]
      true
      (throw (RuntimeException. (str "dt: " (class dt) " not= " (class s)))))))

(defn associativity
  [a b c]
  (log/info "monoid law: associativity")
  (let [s1 (** (** a b) c)
        s2 (** a (** b c))]
    (log/debug "(a * b) * c " s1)
    (log/debug "a * (b * c) " s2)
    (println "(a ** b) ** c = a ** (b ** c) ?  " (= s1 s2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; meta operations - part of the public sig, but constant across models (i.e. non-semantic ops)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def installed-structs
  (array-map
   :default 'algebra.structure.monoid.default
   :n0 'algebra.structure.monoid.N0
   :n1 'algebra.structure.monoid.n1
   :quotient-3 'algebra.structure.monoid.q3
   ))

(def ^:dynamic *active-model* :default)

;; map struct keys to dispatch param objects
(defonce dispatch-types (atom {}))
(declare try-load-model get-struct-kw dispatch-type)

(defn activate!
  "Set the active model, which determines interpretation of Group ops.  Arg is key-or-struct."
  ([kors]
   (log/debug "activate!: " kors)
    (when (keyword? kors)
      (let [m (try-load-model kors)
            log (log/debug "m: " m)
            c (constants m)
            e (:id c)
            kw (get-struct-kw kors)]
        (log/debug "loaded model: " m)
        (alter-var-root (var *active-model*)
                        (fn [_] kw))
        (log/debug "*active-model*: " *active-model*)
        (log/debug "dispatch-types: " @dispatch-types)
        (log/debug "@id: " @id " to " e)
        (reset! id e)
        (log/debug "@id: " @id)
        ;; (alter-var-root (var *id*)
        ;;                 (fn [_] e))

        ))))

(defn install!
  "Registers a structure for use in a Group model."
  ([struct]
   (do
     (log/debug "install 1: " struct (type struct))
     (let [kw (structure struct)]
       (log/debug "install 1 kw: " kw)
       (install! kw struct))))
  ([key struct]
   (do
     (log/debug "install 2: " key struct)
     (if (keyword? key)
       (do
         (log/info (str "dispatch-types: " @dispatch-types))
;         (log/info (str "registering " (name struct), ", model type: " (class struct)))
         (swap! dispatch-types assoc key struct)
         (log/info (str "dispatch-types: " @dispatch-types))
         )
       (throw (RuntimeException. "arg1 must be clojure keyword"))))))

(defn active-model? [t]
  (log/debug "active-model?" t)
  )

(defn active-model [t]
  (log/debug "active-model")
  *active-model*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-struct-kw
  "Returns keyword for struct"
  [struct]
  ;; (log/debug "get-struct-kw: " struct)
  (if (keyword? struct)
    struct
    ;; else invoke `structure` op on implementation
    (structure struct)))

(defn- try-load-model
  ([k]
   (log/debug "try-load-model: " k)
   (log/debug "    dispatch-types: " @dispatch-types)
   (log/debug "    installed-structs: " installed-structs)
   (if-let [model (@dispatch-types k)]
     (do
          (log/debug "model: " model)
          model)
     (if-let [ns-sym (installed-structs k)]
       (do
         (log/debug "ns-sym: " ns-sym)
         (try
           ;; NOTE: in core.matrix, implementations are expected to
           ;; register on load, so they populate dispatch-types
           ;; appropriately when :required.
           (do (require ns-sym)
               (@dispatch-types k))
           (catch Throwable t nil)))
       ;; (log/debug "struct " k "not found")
       ))))

(defn dispatch-type
  "Gets the struct object for a keyword or struct.  The struct object
  will be used to parameterize Group operations by struct type; in
  effect this is a way of dynamically selecting a model to determine
  the interpretion of an operation.

  Arg is keyword-or-struct."
  ([]
   (do
     ;; (log/debug "dispatch-type 0")
     (dispatch-type *active-model*)))
  ([kors]
   (do
     ;; (log/debug "dispatch-type 1" kors)
    (let [k (get-struct-kw kors)
          ;; log (log/debug "struct k: " k)
          obj (@dispatch-types k)
          ;; log (log/debug "struct: " obj)
          ]
      (if k
        (or obj
           (if (try-load-model k) (@dispatch-types k))
           ;; Why? (when-not (keyword? m) m)
           nil)
        nil)))))
