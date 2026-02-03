class Foo {
    private fun test(flag: Boolean = false): String = "Hello"

    fun run() {
        // EXPRESSION: test()
        // RESULT: "Hello": Ljava/lang/String;
        //Breakpoint!
        test()
    }
}

fun main(args: Array<String>) {
    Foo().run()
}