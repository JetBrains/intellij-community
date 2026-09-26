interface E

interface EE : E {
    fun f(): Boolean
}

fun caller(
    elements: List<E>
): Boolean = elements.none {
    it.f<caret>oo()
}

private fun E.foo(): Boolean = this is EE && this.f()