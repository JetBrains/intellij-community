package test

internal open class ClassWithStatics {
    fun instanceMethod() {
    }

    companion object {
        @JvmStatic
        fun staticMethod(p: Int) {
        }

        const val staticField: Int = 1
        @JvmField
        var staticNonFinalField: Int = 1

        var value: Int = 0
    }
}
