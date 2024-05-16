class Foo {
    fun myMethod(isGood: Boolean?): String {
        return if (java.lang.Boolean.TRUE == isGood) "good" else "no"
    }
}
