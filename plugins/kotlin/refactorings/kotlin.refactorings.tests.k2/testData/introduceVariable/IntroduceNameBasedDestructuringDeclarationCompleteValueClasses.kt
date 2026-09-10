// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete -XXLanguage:+FullValueClasses
fun test() {
    <selection>Dimension(1, 2)</selection>
}

value class Dimension(val width: Int, val height: Int)