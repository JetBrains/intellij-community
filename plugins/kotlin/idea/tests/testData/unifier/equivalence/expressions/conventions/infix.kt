infix fun Int.infixFun(other: Int) = this + other

fun test() {
    <selection>1 infixFun 2</selection>
    1.infixFun(2)

    1 infixFun 3
    1.infixFun(3)
    3.infixFun(2)

    infix fun Int.infixFun(other: Int) = this + other
    1 infixFun 2
}