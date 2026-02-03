fun foo() {
    val klass = MyClass()
    klass<caret>[1]
}

class MyClass {
    fun get(i: Int): Int = 1
}

// DISALLOW_METHOD_CALLS
// EXPECTED: null