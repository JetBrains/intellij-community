import Anon.E

internal annotation class Anon(val value: String) {
    enum class E {
        A, B
    }

    companion object {
        val field: E = E.A
    }
}

@Anon("a")
internal interface I {
    companion object {
        val e: E = Anon.Companion.field
    }
}
