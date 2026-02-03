package noReceiverOnStack

fun String.one() = 1

class A {
    var sum = 0

    fun addOne() {
        sum += 1
    }
}

fun buildSum(block: A.() -> Unit) = A().apply(block).sum

fun main(args: Array<String>) {
    // EXPRESSION: buildString { append("Hello!") }
    // RESULT: "Hello!": Ljava/lang/String;
    //Breakpoint!
    buildString { append("Hello!") }

    // EXPRESSION: buildSum { repeat(3) { addOne() } }
    // RESULT: 3: I
    //Breakpoint!
    buildSum { repeat(3) { addOne() } }

    // EXPRESSION: buildString { append(buildSum { buildString { addOne() } }) }
    // RESULT: "1": Ljava/lang/String;
    //Breakpoint!
    buildString { append(buildSum { buildString { addOne() } }) }

    // EXPRESSION: with("Hello!") { one() }
    // RESULT: 1: I
    //Breakpoint!
    with("Hello!") { one() }
}

// IGNORE_FOR_K2_CODE
// Remove ignore after KT-57227 fix