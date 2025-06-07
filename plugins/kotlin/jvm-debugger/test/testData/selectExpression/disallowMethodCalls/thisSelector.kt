class MyClass {
    fun test() {
        this.<caret>test()
    }
}

// DISALLOW_METHOD_CALLS
// EXPECTED: null