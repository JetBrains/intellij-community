package capturedShadowedByLocal

fun foo(f: () -> Unit) = f()

fun main() {
    val s = intArrayOf(31, 32)
    val n = 1
    val k = 3
    foo {
        println(s[0] + n + k)
        run {
            val s = "shadow"
            val n = 2
            //Breakpoint!
            println(s + n)
        }
    }
}

// KTIJ-41215: a local shadows a captured variable, so the frame shows only the local.

// SHOW_KOTLIN_VARIABLES
// PRINT_FRAME
