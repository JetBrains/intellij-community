public class TopLevelFunction {
    public static void foo() {
        k.DependenciesKt.<caret>withJvmOverloads(4);
    }
}

// REF: (k).withJvmOverloads(Int, Boolean, String)
// CLS_REF: (k).withJvmOverloads(Int, Boolean, String)