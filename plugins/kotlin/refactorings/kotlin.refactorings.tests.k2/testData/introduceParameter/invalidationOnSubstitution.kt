// WITH_DEFAULT_VALUE: false
// WITH_STDLIB

inline fun inlineFunction1(block: () -> Unit) {
    inlineFunction2 {
        <caret>block()
    }
}

inline fun inlineFunction2(block: () -> Unit) {
    block()
}

fun testInline() {
    inlineFunction1 {
        println("Hello")
    }
}