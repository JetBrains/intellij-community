data class A(val a: Int, val b: Int, val c: Int)

fun test() {
    fun x(): Int <selection>{
        val (a, b) = A(1, 2, 3)
        return a + b
    }</selection>

    fun y(): Int {
        val (c, d) = A(1, 2, 3)
        return c + d
    }

    fun z(): Int {
        val (a, b) = A(2, 3, 4)
        return a + b
    }

    fun w(): Int {
        val (a, b, c) = A(1, 2, 3)
        return a + b
    }
}