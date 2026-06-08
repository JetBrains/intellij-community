class A {
    operator fun String.invoke() {}
}

fun A.foo(a: String) {
    <selection>a()</selection>
}


