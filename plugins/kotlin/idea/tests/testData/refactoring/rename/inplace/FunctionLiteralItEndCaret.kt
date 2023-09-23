// NEW_NAME: "y"
// RENAME: lambdaParameter

fun f() {
    val f: (Int) -> Int = { it<caret> + it }
}