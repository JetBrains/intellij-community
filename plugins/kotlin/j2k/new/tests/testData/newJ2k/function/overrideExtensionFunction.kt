// ERROR: 'foo' overrides nothing
// ERROR: 'foo2' overrides nothing
// Not supported yet: KTIJ-7583
class J : K() {
    protected override fun foo(receiver: Int) {}

    protected override fun foo2(receiver: Int, s: String, l: Long) {}
}
