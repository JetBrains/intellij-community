// "Replace with dot call" "true"
// LANGUAGE_VERSION: 1.6

fun <T : Any> foo(x: T) {
    val y = x?.toString()<caret>
}
/* IGNORE_FIR */
