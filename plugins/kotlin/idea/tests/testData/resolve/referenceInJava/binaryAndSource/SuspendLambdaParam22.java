public class TopLevelFunction {
    public static void foo() {
        suspendFunctions.SuspendFunctionsKt.<caret>suspendLambdaParam22(null);
    }
}

// REF: (suspendFunctions).suspendLambdaParam22(suspend (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int,) -> Unit)

// no suspend due to KT-81710
// CLS_REF: (suspendFunctions).suspendLambdaParam22((Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) -> Unit)