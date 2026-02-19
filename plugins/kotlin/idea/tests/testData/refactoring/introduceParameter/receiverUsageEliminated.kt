// WITH_DEFAULT_VALUE: false
fun String.bar() = <selection>foo()</selection>.map { it.toInt() }
fun String.foo(): List<String> = TODO()