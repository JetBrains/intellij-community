package a

fun b<caret>() {}

class A {
    companion object {
        fun A.companionFun() {
            b()
        }
    }
}
