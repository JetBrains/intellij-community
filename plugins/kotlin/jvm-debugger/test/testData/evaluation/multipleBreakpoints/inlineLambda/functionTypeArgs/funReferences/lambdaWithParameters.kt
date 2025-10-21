// IGNORE_K1

fun main() {
    foo { i, s -> i.toString() + "_" + s }
}

inline fun foo(block: (Int, String) -> String) {
    // EXPRESSION: block(5, "x")
    // RESULT: "5_x": Ljava/lang/String;
    //Breakpoint!
    val x = 1
}