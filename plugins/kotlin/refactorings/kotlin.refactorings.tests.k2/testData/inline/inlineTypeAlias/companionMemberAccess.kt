class Host {
    companion object {
        val answer = 42
    }
}

typealias <caret>Alias = Host.Companion

val companion = Alias
val answer = Alias.answer
