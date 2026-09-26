package a.b.c

fun s(): String = ""

fun Any.s(): String = toString()

fun foo() {
    Any().s()
    a.b.c.s()
    s()
    <selection>Any()?.s()</selection>
    (Any())?.s()
    (Any()?.s())
}