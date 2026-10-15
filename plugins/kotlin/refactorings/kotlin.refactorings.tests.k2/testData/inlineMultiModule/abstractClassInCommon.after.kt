// PLATFORM: Common
// FILE: Foo.kt
// MAIN
<caret>fun keepFileAlive()

// PLATFORM: Jvm
// FILE: JvmFoo.kt
class JvmFoo : Foo()

// PLATFORM: Js
// FILE: JsFoo.kt
fun test(a: Any) {
    val foo = a as? Foo
}


