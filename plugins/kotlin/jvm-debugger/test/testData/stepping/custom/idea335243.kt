package idea335243

fun foo(x: Int) {
    Any().let {
        it
    }
}

fun foo(x: Int, y: Int) {
    // SMART_STEP_INTO_BY_INDEX: 2
    //Breakpoint!
    Any().let {
        it
    }
}

fun main() {
    foo(1)
    foo(1, 2)
}

// IGNORE_K2
