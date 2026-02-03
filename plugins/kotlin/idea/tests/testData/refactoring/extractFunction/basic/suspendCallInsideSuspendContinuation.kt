// PARAM_TYPES: D
// PARAM_DESCRIPTOR: value-parameter d: D defined in test1
fun m(block: suspend () -> Unit) {}

class D {
    suspend fun await() {}
}

fun test1(d: D) {
    <selection>m {
        d.await()
    }</selection>
}