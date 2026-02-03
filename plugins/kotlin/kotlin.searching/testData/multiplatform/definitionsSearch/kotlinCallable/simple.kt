// PLATFORM: Common
// FILE: A.kt
// MAIN
open expect class A {
    open fun <caret>f() {}
}

open class B : A() {
    override fun f() {}
}

class C : A() {
    override fun f() {}
}

class D : B() {
    override fun f() {}
}

// PLATFORM: Jvm
// FILE: A.kt
open actual class A {
    open actual fun f() {}
}

open class B : A() {
    override fun f() {}
}

class C : A() {
    override fun f() {}
}

class D : B() {
    override fun f() {}
}