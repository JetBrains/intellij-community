package a

class A {
    companion object {
        @JvmStatic
        fun fo<caret>o() = 0

        @JvmStatic var ba<caret>r: Int = 0
    }
}