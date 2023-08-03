interface A {
    val parent: A
}

fun A.<caret>ext(): Int = 1 + parent.ext()
// IGNORE_K2