class J {
    fun dfa(obj: Any?) {
        if (obj is String) {
            // cast to not-null String, which is redundant altogether
            takesString(obj)
        }
    }

    fun notNullVariable(o: Any?) {
        val s = o as String
        println(s.length)
    }

    fun qualifiedCall(o: Any?) {
        if (o == null) return
        val length = (o as String).length
    }

    private fun takesString(s: String?) {
        println(s)
    }
}
