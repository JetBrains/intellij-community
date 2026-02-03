package valueMethodsInSignature

@JvmInline
value class ValueMe(val x: Int)

fun main() {
    val v = ValueMe(1)
    //Breakpoint!
    println()
}

// EXPRESSION: v
// RESULT: instance of valueMethodsInSignature.ValueMe(id=ID): LvalueMethodsInSignature/ValueMe;
