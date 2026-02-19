
class Foo(val i: Int) {

}

fun Foo.m() {
    object : Runnable {
        override fun run() {
            val r = (1 .. 2).find {
                println(i)
                true
            }
            if (<caret>r != 0) { }
        }
    }
}