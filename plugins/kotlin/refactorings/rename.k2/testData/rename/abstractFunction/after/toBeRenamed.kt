sealed class A {
    internal abstract fun absFun2(x:Int): Int

    abstract class B : A() {
        override fun absFun2(x: Int): Int = 1
    }
}