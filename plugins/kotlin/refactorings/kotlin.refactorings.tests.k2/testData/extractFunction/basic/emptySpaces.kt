// WITH_STDLIB
fun main2() {
    listOf(1, 2, 3).forEach { i ->
<selection>        val x = 73
        val y = x + 10
</selection>
        println(i + y)
    }
}