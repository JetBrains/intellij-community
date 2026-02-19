class MyClass {
    fun test() {
        <caret>this.test()
    }
}

// DISALLOW_METHOD_CALLS
// EXPECTED: this