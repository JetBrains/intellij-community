// COMPILER_ARGUMENTS: -Xcontext-parameters

context(t: Int)
fun String.fo<caret>o() {
    t + 5
    this.substring(0)
}