package test

private open var p: Int
    get() = 1
    set(value: Int) {}

fun test() {
    val t = p
    p = 1
}