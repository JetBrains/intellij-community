// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter a: kotlin.Int defined in foo
// SIBLING:
fun foo(a: Int) {
    val b: Int = 1

    <selection>var t = a
    while(t > 0) {
        if (t == 2) continue
        println(t)
        if (t == 1) break
        t--
    }</selection>
}