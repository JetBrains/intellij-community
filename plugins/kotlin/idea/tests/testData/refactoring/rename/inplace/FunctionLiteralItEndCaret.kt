// NEW_NAME: "y"
// RENAME: lambdaParameter
// IGNORE_K2

fun f() {
    val f: (Int) -> Int = { it<caret> + it }
}