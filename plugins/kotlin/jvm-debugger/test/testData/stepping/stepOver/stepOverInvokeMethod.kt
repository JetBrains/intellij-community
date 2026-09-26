package stepOverInvokeMethod

// SKIP_SYNTHETIC_METHODS: true

fun foo(): Boolean {
    // STEP_OVER: 1
    //Breakpoint!
    return true
}

fun bar(f: () -> Boolean) {
    if (!f()) return
}

fun main() {
    bar(::foo)
}
