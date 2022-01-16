package functionReference

fun test(s: () -> String) {
    s()
}

fun a() = "OK"

fun main(args: Array<String>) {
    //Breakpoint!
    test(::a)
}

// STEP_INTO: 7