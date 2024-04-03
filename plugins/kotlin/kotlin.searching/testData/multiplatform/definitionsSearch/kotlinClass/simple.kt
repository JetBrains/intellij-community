// PLATFORM: Common
// FILE: A.kt
// MAIN
expect open class <caret>A()
open class B : A()
class C : A()
class D : B()

// PLATFORM: Jvm
// FILE: A.kt
actual open class A
open class B : A()
class C : A()
class D : B()