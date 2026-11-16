package a

class A {
    companion object {
        open class B<caret>ar {
            open fun nestedFun() {}

            final override fun toString(): String = "Bar"

            companion object {
                @JvmStatic
                fun nestedCompanionFun() {}
            }
        }
    }
}