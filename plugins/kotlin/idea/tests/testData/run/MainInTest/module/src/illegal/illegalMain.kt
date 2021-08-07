package illegal

fun notMain() {
    // no
}

class A {
    @JvmStatic
    fun main(args: Array<String>) {
        // no
    }
    companion object {
        fun foo() {
            @JvmStatic
            fun main(args: Array<String>) {
                // no
            }
        }
    }
}
