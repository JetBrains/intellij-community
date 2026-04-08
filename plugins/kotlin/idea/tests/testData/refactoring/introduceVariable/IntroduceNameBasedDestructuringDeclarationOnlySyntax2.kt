// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// IGNORE_K1
fun test() {
    <selection>Dimension(0, 0)</selection>
    (val width, val height) = Dimension(1, 2)
}

data class Dimension(val width: Int, val height: Int)