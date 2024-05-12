// WITH_STDLIB

interface I {
    fun f()
}

class A: I {
    override fun f() {
        val p = <selection>42</selection>
    }
}