package test

class A {
    companion object {
        fun <caret>gone() {}

        fun kept() {}
    }
}
