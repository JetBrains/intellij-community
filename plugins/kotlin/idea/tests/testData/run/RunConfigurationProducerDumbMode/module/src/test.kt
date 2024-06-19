package q

// NO_CONFIGURATION_IN_DUMB_MODE
fun main() {
    println("Top-level")
}

// NO_CONFIGURATION_IN_DUMB_MODE
fun main(args: Array<String>) {

}

// NO_CONFIGURATION_IN_DUMB_MODE
object Foo {
    // NO_CONFIGURATION_IN_DUMB_MODE
    @JvmStatic fun main(s: Array<String>) {
        println("Foo")
    }

    // NO_CONFIGURATION_IN_DUMB_MODE
    @JvmName("main")
    @JvmStatic fun aaa(s: Array<String>) {}

    // NO_CONFIGURATION_IN_DUMB_MODE
    class InnerFoo {
        companion object {
            // NO_CONFIGURATION_IN_DUMB_MODE
            @JvmStatic fun main(s: Array<String>) {
                println("InnerFoo")
            }

            // NO_CONFIGURATION_IN_DUMB_MODE
            @JvmName("main")
            @JvmStatic fun aaa(s: Array<String>) {}
        }
    }
}