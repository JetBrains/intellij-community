package a

fun b<caret>() {}

class A {
    class Nested {
        fun nested() {
            b()
        }
    }
}
