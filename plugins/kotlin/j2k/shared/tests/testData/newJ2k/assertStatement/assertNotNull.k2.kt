internal abstract class C {
    var mMemberVariable: String? = null

    fun foo() {
        val s1 = checkNotNull(f())
        val s2 = checkNotNull(g()) { "g should not return null" }
        val doNotMergeDueToPossibleSideEffects = g()
        assert(s2.hashCode() == 42)
        val h = s2.hashCode()

        val s3 = "sss"
        checkNotNull(s3.hashCode())

        checkNotNull(doNotMergeDueToPossibleSideEffects)
        checkNotNull(mMemberVariable)

        val doNotTouchDifferentAssert = f()
        assert(doNotTouchDifferentAssert == null)
    }

    abstract fun f(): String
    abstract fun g(): String
}
