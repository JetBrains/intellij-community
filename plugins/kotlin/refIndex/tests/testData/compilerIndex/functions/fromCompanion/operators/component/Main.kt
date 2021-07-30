class Main {
    companion object {
        operator fun Int.component1(): String = ""
        operator fun Int.compo<caret>nent2(): String = ""
    }
}