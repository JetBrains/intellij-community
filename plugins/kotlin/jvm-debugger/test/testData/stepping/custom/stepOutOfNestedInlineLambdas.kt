package stepOutOfNestedInlineLambdas

inline fun foo(f: () -> Int): Int {
    var x = 1
    x += x.bar { x +  1 }
    return f()
}

inline fun Int.bar(f: () -> Int): Int {
    var result = f()
    result *= 2
    return result
}

fun main() {
    var x = 1
    x.let {
        foo {
            foo { 1 }.bar {
                // STEP_OUT: 5
                //Breakpoint!
                x += 1
                x * 2
            }
            foo { 1 }.bar {
                2
            }
        }
        var y = 3
    }
}
