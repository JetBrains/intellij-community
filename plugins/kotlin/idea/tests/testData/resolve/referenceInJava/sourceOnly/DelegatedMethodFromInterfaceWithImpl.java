import k.*;

public class Test {
    public static void bar(InterfaceWithDelegatedWithImpl some) {
        some.<caret>foo();
    }
}

// REF: (in k.InterfaceWithImpl).foo()
