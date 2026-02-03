class Foo<T>

fun f() {
    val v : Foo<Int> = <selection>Foo()</selection>
    val u : Foo<String> = Foo()
    val w = Foo<Int>()
}