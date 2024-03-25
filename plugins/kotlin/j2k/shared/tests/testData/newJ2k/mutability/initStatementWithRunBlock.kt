class SomeClass(paramString: String) {
    private var aString: String? = null
    private var bString: String? = null
    private val cString: String

    init {
        run {
            aString = "hello"
        }
        if (paramString === "String") {
            bString = "goodbye"
        }
        cString = paramString + "c"
    }
}
