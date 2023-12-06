class Foo {
    fun run() {
        // EXPRESSION: test()
        // RESULT: "Hello": Ljava/lang/String;
        //Breakpoint!
        test()
    }
}

fun Foo.test(flag: Boolean = false): String = "Hello"

fun main(args: Array<String>) {
    Foo().run()
}