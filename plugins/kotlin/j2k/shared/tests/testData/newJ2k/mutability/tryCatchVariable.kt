class SomeClass {
    fun convertPatterns() {
        try {
            val ok: Boolean
            try {
                ok = doConvertPatterns()
            } catch (ignored: MalformedPatternException) {
                ok = false
            }
            if (!ok) {
                println("things are not okay")
            }
        } finally {
            myWildcardPatternsInitialized = true
        }
    }
}