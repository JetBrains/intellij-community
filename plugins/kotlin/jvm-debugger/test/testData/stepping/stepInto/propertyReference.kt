package propertyReference

fun test(s: () -> String) {
    s()
}

val a: String get() = "OK"

fun main(args: Array<String>) {
    //Breakpoint!
    test(::a)
}

// STEP_INTO: 7