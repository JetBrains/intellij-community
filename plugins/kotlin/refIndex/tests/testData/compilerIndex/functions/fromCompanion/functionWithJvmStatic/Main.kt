package top.level

class Main {
    companion object {
        @JvmStatic
        fun companionFunction<caret>() = Unit
    }

    fun t() {
        companionFunction()
    }
}