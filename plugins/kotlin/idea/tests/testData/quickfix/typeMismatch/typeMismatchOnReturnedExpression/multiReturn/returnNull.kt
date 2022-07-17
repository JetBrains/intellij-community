// "Change return type of enclosing function 'test' to 'String?'" "true"
fun test() {
    if (true) return "foo"<caret>
    return null
}

/* IGNORE_FIR */
