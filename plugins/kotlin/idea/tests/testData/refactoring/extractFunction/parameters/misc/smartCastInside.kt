class X(val field: Any?)

fun foo(list: List<X>) {
    <selection>for (x in list) {
        if (x.field != null) {
            println(x.field.hashCode())
        }
    }</selection>
}

// IGNORE_K1