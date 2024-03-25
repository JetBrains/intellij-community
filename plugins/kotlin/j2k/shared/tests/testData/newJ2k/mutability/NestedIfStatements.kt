internal class A {
    fun stringAssignment(): String {
        if (Math.random() > 0.50) {
            return "a string"
        }
        return "b string"
    }

    fun aVoid() {
        var aString: String
        val bString = stringAssignment()
        val cString: String
        if (stringAssignment().startsWith("a")) {
            aString = stringAssignment()
            cString = "aaaa"
            if (aString === bString) {
                aString = stringAssignment()
            }
        } else if (stringAssignment().startsWith("b")) {
            aString = "bbbb"
            cString = "bbbb"
        } else {
            aString = "cccc"
            cString = "cccc"
        }
    }
}
