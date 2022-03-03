// "Change return type of enclosing function 'test' to 'String?'" "true"
fun test(x: String?) {
    if (true) return "foo"<caret>
    return x
}