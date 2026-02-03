// ERROR: 'foo' overrides nothing. Potential signatures for overriding: fun Int.foo(): Unit
// ERROR: 'foo2' overrides nothing. Potential signatures for overriding: fun Int.foo2(s: String, l: Long): Unit
// Not supported yet: KTIJ-7583
class J : K() {
    override fun foo(receiver: Int) {}

    override fun foo2(receiver: Int, s: String?, l: Long) {}
}
