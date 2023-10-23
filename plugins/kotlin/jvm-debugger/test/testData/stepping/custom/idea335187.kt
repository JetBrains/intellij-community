package idea335187

fun main(args: Array<String>) {
    foo {
        fun bar() {}
        bar()

        //Breakpoint!
        println("Hello World")
    }
}

fun foo(body: () -> Unit) = body()
