// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// IGNORE_K1
fun test() {
    <selection>Dimension(1, 2)</selection>
}

data class Dimension(val width: Int, val height: Int)