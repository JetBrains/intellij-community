sealed class A {
    internal abstract fun absFun(x:Int): Int

    abstract class B : A() {
        override fun absFun(x: Int): Int = 1
    }
}