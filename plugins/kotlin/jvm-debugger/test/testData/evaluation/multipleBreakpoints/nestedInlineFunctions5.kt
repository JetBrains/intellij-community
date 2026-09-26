package nestedInlineFunctions5

inline fun foo(fooParam: Int, block: (Int) -> Unit) {
    val fooVar = 1
    baz(1)
    block(1)
}

inline fun bar(barParam: Int, block: (Int) -> Unit) {
    val barVar = 2
    block(1)
}

inline fun baz(bazParam: Int) {
    val bazVar = 3
    baz1(1) { baz1BlockParam ->
        val baz1LambdaVar = 1
        baz1(2) { baz1BlockParam1 ->
            val baz1LambdaVar1 = 1
			//Breakpoint!
			println()
            3
        }
		//Breakpoint!
		println()
        2
    }
}

inline fun baz1(baz1Param: Int, block: (Int) -> Int) {
    val baz1Var = 3
    baz2(1)
    block(1)
}

inline fun baz2(baz2Param: Int) {
    val baz2Var = 3
	//Breakpoint!
	println()
}

inline fun flaf() {
    val flafVar = 1
    foo(1) { fooLambdaParam ->
        val fooLambdaVar = 2
        bar(2) { barLambdaParam ->
            val barLambdaVar = 3
			//Breakpoint!
			println(flafVar)
        }
		//Breakpoint!
		println(flafVar)
    }
}

fun main() {
    val mainVar = 1
    flaf()
    foo(1) { fooLambdaParam ->
        val fooLambdaVar = 2
        bar(2) { barLambdaParam ->
            val barLambdaVar = 3
			//Breakpoint!
			println(mainVar)
        }
		//Breakpoint!
		println(mainVar)
    }
}

// EXPRESSION: baz2Param + baz2Var
// RESULT: 4: I

// EXPRESSION: baz2Param + baz2Var
// RESULT: 4: I

// EXPRESSION: bazParam + bazVar + baz1BlockParam + baz1LambdaVar + baz1BlockParam1 + baz1LambdaVar1
// RESULT: 8: I

// EXPRESSION: bazParam + bazVar + baz1BlockParam + baz1LambdaVar
// RESULT: 6: I

// EXPRESSION: flafVar + fooLambdaParam + fooLambdaVar + barLambdaParam + barLambdaVar
// RESULT: 8: I

// EXPRESSION: flafVar + fooLambdaParam + fooLambdaVar
// RESULT: 4: I

// EXPRESSION: baz2Param + baz2Var
// RESULT: 4: I

// EXPRESSION: baz2Param + baz2Var
// RESULT: 4: I

// EXPRESSION: bazParam + bazVar + baz1BlockParam + baz1LambdaVar + baz1BlockParam1 + baz1LambdaVar1
// RESULT: 8: I

// EXPRESSION: bazParam + bazVar + baz1BlockParam + baz1LambdaVar
// RESULT: 6: I

// EXPRESSION: mainVar + fooLambdaParam + fooLambdaVar + barLambdaParam + barLambdaVar
// RESULT: 8: I

// EXPRESSION: mainVar + fooLambdaParam + fooLambdaVar
// RESULT: 4: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
