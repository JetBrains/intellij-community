package a

class A {
    companion {
        class Bar {
            open fun nestedFun() {}

            final override fun toString(): String = "Bar"

            companion object {
                @JvmStatic
                fun nestedCompanionFun() {}
            }
        }
    }
}