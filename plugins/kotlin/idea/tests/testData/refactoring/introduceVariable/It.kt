// IGNORE_K2
fun a(op: (Int) -> Int) {}
fun b() {
    a {it}
    a {
        <selection>it</selection>
    }
}