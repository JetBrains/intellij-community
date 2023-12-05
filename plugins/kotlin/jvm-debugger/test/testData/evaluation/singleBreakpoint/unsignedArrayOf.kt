package unsignedArrayOf

fun main(args: Array<String>) {
    //Breakpoint!
    args.size
}

// Note the result is unboxed. IDEA-337026
// EXPRESSION: ulongArrayOf(1u, 2u, 3u)
// RESULT: instance of long[3] (id=ID): [J

// The result is boxed in K2
// IGNORE_K2