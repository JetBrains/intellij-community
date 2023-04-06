// ERROR: 'foo' overrides nothing
// Not supported yet: KTIJ-7583
class J : K() {
    protected override fun foo(receiver: Int) {}
}
