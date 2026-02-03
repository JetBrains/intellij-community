public class TopLevelFunction {
    public static void foo() {
        suspendFunctions.SuspendFunctionsKt.<caret>suspendFun(null);
    }
}

// REF: (suspendFunctions).suspendFun()
// CLS_REF: (suspendFunctions).suspendFun()