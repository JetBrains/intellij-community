package top.level

class Main {
    companion object {
        fun companionFunction<caret>() = Unit
    }

    fun t() {
        companionFunction()
    }
}