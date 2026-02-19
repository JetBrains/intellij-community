class Derived: Base() {
    fun test() {
        <caret>super.test()
    }
}

open class Base {
    fun test() {}
}

// DISALLOW_METHOD_CALLS
// EXPECTED: null