// IGNORE_K1
interface A
interface B

fun foo() {
    object : B by <selection>object : A, B {}</selection> {}
}