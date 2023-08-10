internal class A {
    /* rare nullable, handle with caution */
    fun nullableString(): String? {
        if (Math.random() > 0.999) {
            return "a string"
        }
        return null
    }

    fun takesNotNullString(s: String) {
        println(s.substring(1))
    }

    fun aVoid() {
        var aString: String?
        if (nullableString() != null) {
            aString = nullableString()
            if (aString != null) {
                for (i in 0..9) {
                    takesNotNullString(aString!!) // Bang-bang here
                    aString = nullableString()
                }
            } else {
                aString = "aaa"
            }
        } else {
            aString = "bbbb"
        }
    }
}
