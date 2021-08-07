// "Use fully qualified call" "true"

object Test1 {
    fun <T> foo(f: () -> T): T = f()
    fun bar(): Int = 0

    object Scope {
        fun bar(x: Int = 0): String = ""

        fun test() {
            val result = foo(::<caret>bar)
        }
    }
}
