import k.*;

public class Test {
    public static void bar(InterfaceWithDelegatedNoImpl some) {
        some.<caret>foo();
    }
}

// REF: (in k.InterfaceNoImpl).foo()
