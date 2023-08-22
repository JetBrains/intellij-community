package genericWithMultipleUpperBounds

interface I1 {
    fun foo(): String
}

interface I2 {
    fun bar(): String
}

class A : I1, I2 {
    override fun foo() = "A.foo"

    override fun bar() = "A.bar"
}

fun <X> use(x: X) where X : I1, X : I2 {
    // EXPRESSION: x.foo() + " " + x.bar()
    // RESULT: "A.foo A.bar": Ljava/lang/String;
    //Breakpoint!
    val a = 1
}

fun main(args: Array<String>) {
    use(A())
}
