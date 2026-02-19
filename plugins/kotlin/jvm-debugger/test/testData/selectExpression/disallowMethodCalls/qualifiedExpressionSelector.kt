fun foo() {
    val klass = MyClass()
    klass.<caret>bar()
}

class MyClass {
    fun bar() = 1
}

// DISALLOW_METHOD_CALLS
// EXPECTED: null