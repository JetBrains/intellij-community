public class TopLevelFunction {
    public static void foo() {
        suspendFunctions.SuspendFunctionsKt.<caret>suspendLambdaParam1(null);
    }
}

// REF: (suspendFunctions).suspendLambdaParam1(suspend (Int) -> Unit)

// no suspend due to KT-81710
// CLS_REF: (suspendFunctions).suspendLambdaParam1((Int) -> Unit)