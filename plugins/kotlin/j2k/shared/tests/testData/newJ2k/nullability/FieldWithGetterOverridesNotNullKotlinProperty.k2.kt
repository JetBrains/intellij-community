// ERROR: 'getS' overrides nothing.
// ERROR: 's' hides member of supertype 'I' and needs an 'override' modifier.
class A(private val s: String?) : I {
    override fun getS(): String {
        return s!!
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is A) return false

        val a = o

        return if (s != null) (s == a.s) else a.s == null
    }

    override fun hashCode(): Int {
        return if (s != null) s.hashCode() else 0
    }
}
