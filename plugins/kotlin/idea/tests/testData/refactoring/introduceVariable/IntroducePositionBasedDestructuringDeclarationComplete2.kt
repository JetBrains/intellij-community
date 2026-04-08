// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// IGNORE_K1
fun test() {
    <selection>"a" to "b"</selection>
    val [first, second] = 1 to "2"
}