internal class A {
    var bar: String? = null

    internal inner class J {
        fun getBar(): String {
            return "bar"
        }
    }
}
