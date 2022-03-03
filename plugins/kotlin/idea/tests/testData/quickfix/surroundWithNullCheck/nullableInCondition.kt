// "Surround with null check" "true"
// WITH_STDLIB

fun foz(arg: String?) {
    if (arg<caret>.isNotEmpty()) {

    }
}