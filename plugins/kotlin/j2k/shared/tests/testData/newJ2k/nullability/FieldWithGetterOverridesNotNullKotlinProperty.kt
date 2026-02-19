class A(override val s: String) : I {
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is A) return false

        val a = o

        return if (s != null) (s == a.s) else a.s == null
    }

    override fun hashCode(): Int {
        return s?.hashCode() ?: 0
    }
}
