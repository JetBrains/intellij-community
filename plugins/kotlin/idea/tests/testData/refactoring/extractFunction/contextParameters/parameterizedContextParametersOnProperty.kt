// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c: A)
val <A> p: A
    get() = c

interface MyFormatter {
    fun format(input: String): String
}

context(param: String)
fun MyFormatter.problematicFunction(): String {
    <selection>with(this) {
        return p.format("Value: $param")
    }</selection>
}
// IGNORE_K1