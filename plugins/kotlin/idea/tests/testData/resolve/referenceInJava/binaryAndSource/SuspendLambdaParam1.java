public class TopLevelFunction {
    public static void foo() {
        suspendFunctions.SuspendFunctionsKt.<caret>suspendLambdaParam1(null);
    }
}

// REF: (suspendFunctions).suspendLambdaParam1(suspend (Int) -> Unit)
// CLS_REF: (suspendFunctions).suspendLambdaParam1(suspend (Int) -> Unit)