// ERROR: EXPOSED_SUPER_CLASS
internal abstract class Base {
    abstract fun test()
}

class Test : Base() {
    override fun test() {
    }
}
