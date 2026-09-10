// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
fun test() {
    <selection>Dimension(1, 2)</selection>
}

data class Dimension(val width: Int, val height: Int)