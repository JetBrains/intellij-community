package suspendFunctions

suspend fun suspendFun() {}

fun suspendLambdaParam0(action: suspend () -> Unit) {}

fun suspendLambdaParam1(action: suspend (Int) -> Unit) {}

// kotlin.coroutines.SuspendFunction21 -> kotlin.jvm.functions.Function22
fun suspendLambdaParam21(action: suspend (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int,) -> Unit) {}

// kotlin.coroutines.SuspendFunction22 -> kotlin.jvm.functions.FunctionN (due to big arity)
fun suspendLambdaParam22(action: suspend (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int,) -> Unit) {}
