import dependency.B

fun test(b: B) {
    b.foo <selection>{ it == b.a }</selection>
}