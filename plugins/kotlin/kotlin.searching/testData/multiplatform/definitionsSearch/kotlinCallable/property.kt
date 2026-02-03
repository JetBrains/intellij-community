// PLATFORM: Common
// FILE: A.kt
// MAIN
open expect class A {
    open val <caret>p = ""
}

open class B : A() {
    override val p = ""
}

class C : A() {
    override val p = ""
}

class D : B() {
    override val p = ""
}

// PLATFORM: Jvm
// FILE: A.kt
open actual class A {
    open actual val p = ""
}

open class B : A() {
    override val p = ""
}

class C : A() {
    override val p = ""
}

class D : B() {
    override val p = ""
}