package asyncLambdas

suspend fun main() {
    foo()
}

fun use(value: Int) {

}

val foo: suspend () -> Unit = {
    val a = 5
    bar()
    use(a)
}

val bar: suspend () -> Unit = {
    val b = 3
    baz()
    use(b)
}

val baz: suspend () -> Unit = {
    //Breakpoint!
    println("")
}