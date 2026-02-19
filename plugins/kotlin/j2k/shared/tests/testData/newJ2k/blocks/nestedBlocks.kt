internal class Foo {
    fun x() {
        run {
            run {
                val a = 1
                System.out.printf("%d\n", a)
            }
            run {
                val a = 2
                System.out.printf("%d\n", a)
            }
        }
    }
}
