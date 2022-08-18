fun foo() {
    val klass = MyClass()
    <caret>klass.bar()
}

class MyClass {
    fun bar() = 1
}

// DISALLOW_METHOD_CALLS
// EXPECTED: klass