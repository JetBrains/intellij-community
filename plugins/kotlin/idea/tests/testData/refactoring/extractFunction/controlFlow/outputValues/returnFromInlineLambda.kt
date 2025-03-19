inline fun <R> inFun(block: (Any) -> R): R = TODO()
fun foo(): Any {
    <selection>inFun { a -> return a }</selection>
}
// IGNORE_K1