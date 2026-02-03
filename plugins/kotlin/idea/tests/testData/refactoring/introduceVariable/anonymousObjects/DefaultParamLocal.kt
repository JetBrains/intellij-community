// IGNORE_K1
interface A
interface B

fun bar() {
    fun foo(b: B = <selection>object : A, B {}</selection>) {}
}