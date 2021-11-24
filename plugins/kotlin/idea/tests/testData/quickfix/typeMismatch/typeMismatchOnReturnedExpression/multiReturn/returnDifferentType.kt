// "Change return type of enclosing function 'test' to 'Any'" "true"
fun test(x: Int) {
    if (true) return "foo"<caret>
    return x
}
