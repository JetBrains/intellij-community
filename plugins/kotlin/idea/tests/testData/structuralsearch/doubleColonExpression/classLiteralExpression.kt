fun foo(x: Any) { print(x) }

class X { class Y { class Z { class Int } } }

fun main() {
    foo(<warning descr="SSR">Int::class</warning>)
    foo(<warning descr="SSR">kotlin.Int::class</warning>)
    foo(<warning descr="SSR">X.Y.Z.Int::class</warning>)
    foo(<warning descr="SSR">Int::class</warning>.java)
}