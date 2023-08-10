package sequentialInlineFunctionCalls

inline fun simpleInlineFun() {
    //Breakpoint!
    println()
}

inline fun Any.inlineExt(): Any {
    //Breakpoint!
    return this
}

fun Any.normalExt(): Any {
    //Breakpoint!
    return this
}

inline fun complexInlineFun() {
    //Breakpoint!
    12.inlineExt().normalExt().inlineExt().normalExt()
    //Breakpoint!
    12.normalExt().normalExt()
}

inline fun nestedInlineFun() {
    //Breakpoint!
    simpleInlineFun()
    //Breakpoint!
    complexInlineFun()
}

inline fun doubleNestedInlineFun() {
    //Breakpoint!
    nestedInlineFun().inlineExt().normalExt().inlineExt()
}

fun main() {
    simpleInlineFun()
    simpleInlineFun()
    complexInlineFun()
    nestedInlineFun()
    doubleNestedInlineFun()
}

// RESUME: 36
