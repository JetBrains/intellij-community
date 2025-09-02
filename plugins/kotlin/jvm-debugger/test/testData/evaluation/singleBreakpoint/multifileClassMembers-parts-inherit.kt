// IGNORE_K1
// ATTACH_LIBRARY: multifile-parts-inherit

fun main() {
    //Breakpoint!
    val x = 1
}

// EXPRESSION: privateVarDefaultAccessors = 1; privateVarDefaultAccessors
// RESULT: 1: I

// EXPRESSION: internalValDefaultAccessors = 2; internalValDefaultAccessors
// RESULT: 2: I

// EXPRESSION: publicValDefaultAccessors = 3; publicValDefaultAccessors
// RESULT: 3: I

// EXPRESSION: privateFun()
// RESULT: 4: I

// EXPRESSION: internalFun()
// RESULT: 5: I

// EXPRESSION: publicFun()
// RESULT: 6: I

// EXPRESSION: MyClass().prop
// RESULT: 7: I

// EXPRESSION: MyClass().foo()
// RESULT: 8: I