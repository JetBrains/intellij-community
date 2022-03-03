// "Change return type of enclosing function 'test' to 'CharSequence'" "true"
fun test(x: CharSequence) {
    if (true) return "foo"<caret>
    return x
}