// IGNORE_K2
fun Any.s(): String = toString()

fun foo() {
    Any().s()
    <selection>Any()?.s()</selection>
}