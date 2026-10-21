// PLATFORM: Common
// FILE: Foo.kt
// MAIN
// CONFLICT: JvmFoo
// CONFLICT: Common
abstract class F<caret>oo
fun keepFileAlive()
fun test(a: Any) {
    val foo = a as? Foo
}

// PLATFORM: Jvm
// FILE: JvmFoo.kt
class JvmFoo : Foo()
