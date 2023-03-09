open class A(open val <caret>i : Int)
class B : A(1) {
    override val i: Int
        get() = 1
}