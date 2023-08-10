// WITH_STDLIB
fun main(args: Array<String>) {
    with(A()) {
        println(<selection>prop</selection>)
        println(prop)
    }
}

class A {
    val prop = 1
}