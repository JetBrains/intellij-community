internal class A {
    fun stringAssignment(): String {
        if (Math.random() > 0.50) {
            return "a string"
        }
        return "b string"
    }

    fun aVoid() {
        val aString: String
        val bString = stringAssignment()
        var cString: String? = null
        if (bString.startsWith("b")) {
            aString = "bbbb"
            cString = "cccc"
        } else {
            aString = stringAssignment()
            cString = stringAssignment()
        }
    }
}
