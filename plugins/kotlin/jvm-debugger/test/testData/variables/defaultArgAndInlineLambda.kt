// Covers KT-18574
// Should be updated when KT-79835 is fixed

// SHOW_KOTLIN_VARIABLES

inline fun <X> inlineXU(
    p: X, f: (X) -> Unit = {
        //Breakpoint!
        println(p.toString())
    }
) {
    //Breakpoint!
    return f(p)
}

fun main() {
    inlineXU("abc")
}