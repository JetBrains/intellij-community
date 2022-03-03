// "Change return type of enclosing function 'test' to 'String?'" "true"
fun test() {
    if (true) return "foo"
    return null<caret>
}