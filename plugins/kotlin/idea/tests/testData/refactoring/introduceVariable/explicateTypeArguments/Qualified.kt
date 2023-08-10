// WITH_STDLIB
fun <T> String.ext(): List<T> = listOf()

fun f() {
    val v : List<Int> = <selection>"".ext()</selection>
}
