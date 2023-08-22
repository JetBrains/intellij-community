class MyClass {
    fun Int.test() {
        <caret>this@MyClass
    }
}

// DISALLOW_METHOD_CALLS
// EXPECTED: this@MyClass