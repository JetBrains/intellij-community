// ERROR: Argument type mismatch: actual type is '(String) -> Boolean', but '(String?) -> Boolean' was expected.
package demo

class TestJava {
    fun f(result: Function1<String?, Unit?>) {
        result.invoke("a")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val x: MutableList<String?> = ArrayList<String?>()
            x.filter<String?> { o: String -> o == "a" }
            val lazy = lazy<String?>(LazyThreadSafetyMode.NONE) { "aaa" }
        }
    }
}
