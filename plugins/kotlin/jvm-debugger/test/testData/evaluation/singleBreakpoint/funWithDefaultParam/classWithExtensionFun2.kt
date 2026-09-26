class Bar

class Foo {

    fun Bar.test(flag: Boolean = false): String = "Hello"

    fun run() {
        // EXPRESSION: Bar().test()
        // RESULT: "Hello": Ljava/lang/String;
        //Breakpoint!
        Bar().test()
    }
}


fun main(args: Array<String>) {
    Foo().run()
}