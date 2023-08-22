package a

fun foo() {
    val klass = MyClass()
    <caret>a.MyClass()
}

class MyClass {
    val bar = 1
}

// EXPECTED: a.MyClass()
// EXPECTED_LEGACY: null