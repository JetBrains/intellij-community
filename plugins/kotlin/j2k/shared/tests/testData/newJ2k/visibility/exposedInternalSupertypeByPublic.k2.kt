// ERROR: 'public' subclass exposes its 'internal' supertype 'Base'.
internal abstract class Base {
    abstract fun test()
}

class Test : Base() {
    override fun test() {
    }
}
