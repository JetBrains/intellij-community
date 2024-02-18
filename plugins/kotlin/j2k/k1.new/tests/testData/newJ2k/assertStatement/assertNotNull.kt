internal abstract class C {
    var mMemberVariable: String? = null

    fun foo() {
        val s1 = f()!!
        val s2 = g() ?: error("g should not return null")
        val doNotMergeDueToPossibleSideEffects = g()
        assert(s2.hashCode() == 42)
        val h = s2.hashCode()

        val s3 = "sss"
        assert(s3.hashCode() != null)
        assert(doNotMergeDueToPossibleSideEffects != null)
        assert(mMemberVariable != null)
        val doNotTouchDifferentAssert = f()
        assert(doNotTouchDifferentAssert == null)
    }

    abstract fun f(): String?
    abstract fun g(): String?
}