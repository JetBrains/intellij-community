package arrayMethods

fun main(args: Array<String>) {
    //Breakpoint!
    val a = 5
}

// Calling original `clone()` method fails on JBR 17 with `java.lang.reflect.InaccessibleObjectException`
fun Array<String>.cloneMock() =
    clone()

// EXPRESSION: args.toString().length > 0
// RESULT: 1: Z

// EXPRESSION: args.hashCode() and 0
// RESULT: 0: I

// EXPRESSION: args.cloneMock()
// RESULT: instance of java.lang.String[0] (id=ID): [Ljava/lang/String;