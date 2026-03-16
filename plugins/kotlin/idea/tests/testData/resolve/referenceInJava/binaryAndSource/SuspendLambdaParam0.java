public class TopLevelFunction {
    public static void foo() {
        suspendFunctions.SuspendFunctionsKt.<caret>suspendLambdaParam0(null);
    }
}

// REF: (suspendFunctions).suspendLambdaParam0(suspend () -> Unit)
// CLS_REF: (suspendFunctions).suspendLambdaParam0(suspend () -> Unit)