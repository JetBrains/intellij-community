import O.bar

object O {
    fun bar(): Int = 1
}

fun test() {
    <selection>bar()</selection>
    bar()
}