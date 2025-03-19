class X(val field: Any?)

fun foo(x: X, y: X) {
    if (x.field != null && y.field != null) {
        <selection>println(x.field.hashCode())
        println(x.field.hashCode())
        println(y.field.hashCode())</selection>
    }
}
// IGNORE_K1