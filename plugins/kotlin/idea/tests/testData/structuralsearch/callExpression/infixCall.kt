infix fun Int.foo(any: Any?): Any? { return any }

infix fun Int.bar(any: Any?): Any? { return any }

fun main() {
    val x = 0
    <warning descr="SSR">x foo true</warning>
    x bar true
    x foo false
}