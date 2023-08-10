fun foo() {
    val klass = MyClass()
    klass.<caret>bar
}

class MyClass {
    val bar = 1
}

// DISALLOW_METHOD_CALLS
// EXPECTED: klass.bar