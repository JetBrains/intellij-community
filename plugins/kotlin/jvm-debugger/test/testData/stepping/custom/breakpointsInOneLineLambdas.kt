package breakpointsInOneLineLambdas

fun foo(f: (Int) -> Unit) = f(1)

inline fun Int.bar(f: (Int) -> Int) = f(this)

fun main() {
    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = 1)
    foo { "${println(it)}"}
    //Breakpoint! (lambdaOrdinal = 1)
    foo { 1.bar { it }.bar { it + 1 }.bar { it + 2} }
}
