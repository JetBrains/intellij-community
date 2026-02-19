package test

class A {
    companion object {
        fun foo() {

        }
    }
}

fun test() {
    <selection>A.Companion::class
    (A.Companion)::class</selection>
}