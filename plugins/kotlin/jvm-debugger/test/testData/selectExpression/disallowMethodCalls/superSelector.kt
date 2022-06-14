class Derived: Base() {
    fun test() {
        super.<caret>test()
    }
}

open class Base {
    fun test() {}
}

// DISALLOW_METHOD_CALLS
// EXPECTED: null