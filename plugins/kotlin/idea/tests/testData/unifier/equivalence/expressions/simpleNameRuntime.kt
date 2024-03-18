package p.q.r

var a = 1
val b = <selection>a</selection> + 1

class Bar {
    val a: Int = 1
}

fun with(bar: Bar, lambda: Bar.() -> Unit) {}

fun foo() {
    println(a - 1)
    println(p.q.r.a - 1)
    println((a) - 1)

    val bar = Bar()
    with(bar) {
        println(a - 1)
    }
    println(bar.a)
    bar.takeIf { true }?.a

    val a = 2
    println(a - 1)
}