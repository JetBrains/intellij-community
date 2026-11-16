// ERROR: NOTHING_TO_OVERRIDE
// ERROR: NOTHING_TO_OVERRIDE
// Not supported yet: KTIJ-7583
class J : K() {
    override fun foo(receiver: Int) {}

    override fun foo2(receiver: Int, s: String, l: Long) {}
}
