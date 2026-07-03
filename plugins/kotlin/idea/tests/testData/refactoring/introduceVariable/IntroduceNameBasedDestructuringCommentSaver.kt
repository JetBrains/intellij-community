// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
fun test() {
    <selection>Dimension(1, 2) /*some comment*/</selection>
}

data class Dimension(val width: Int, val height: Int)