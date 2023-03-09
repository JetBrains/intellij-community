package t

interface Interface {
    val some : Int get() = 1
}

open class A {
    companion object Companion : Interface {

    }
}

fun test() {
    <caret>A.some
}


// REF: companion object of (t).A

