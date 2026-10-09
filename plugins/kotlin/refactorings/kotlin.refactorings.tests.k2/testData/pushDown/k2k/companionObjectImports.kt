package base

open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        const val ANSWER = 42

        fun answer() = ANSWER

        class Token
    }
}

class Keep
