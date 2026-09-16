package a

fun b<caret>() {}

class A {
    companion object {
        fun companionFun() {
            b()
        }
    }
}
