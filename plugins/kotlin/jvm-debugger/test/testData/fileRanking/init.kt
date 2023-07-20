//FILE: a/a.kt
class A {
    init {
        val a = 5
        val b = 3
    }
}
// PRODUCED_CLASS_NAMES: A

//FILE: b/a.kt
class B {
    init {
        val x = 1
    }
}
// PRODUCED_CLASS_NAMES: B