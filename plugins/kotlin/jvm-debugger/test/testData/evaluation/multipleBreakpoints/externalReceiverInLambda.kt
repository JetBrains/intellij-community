package externalReceiverInLambda

fun main(args: Array<String>) {
    val sb = StringBuilder()

    // EXPRESSION: run { sb.append("Hello!").toString() }
    // RESULT: "Hello!": Ljava/lang/String;
    //Breakpoint!
    run { sb.append("Hello!").toString() }

    // EXPRESSION: buildString { append(sb.toString()) }
    // RESULT: "Hello!": Ljava/lang/String;
    //Breakpoint!
    buildString { append(sb.toString()) }
}