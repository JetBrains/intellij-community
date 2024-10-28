package breakpointInLambdaInEnum

enum class Foo(val callback: () -> Unit) {
    FOO({
            //Breakpoint!
            val x = 1
        })
}

fun main() {
    Foo.FOO.callback()
}
