public class TopLevelFunction {
    public static void foo() {
        suspendFunctions.SuspendFunctionsKt.<caret>suspendLambdaParam0(null);
    }
}

// REF: (suspendFunctions).suspendLambdaParam0(suspend () -> Unit)

// no suspend due to KT-81710
// CLS_REF: (suspendFunctions).suspendLambdaParam0(() -> Unit)