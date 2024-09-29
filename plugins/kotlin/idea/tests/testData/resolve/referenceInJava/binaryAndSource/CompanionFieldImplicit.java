import fields.ClassWithCompanion;

public class CompanionFieldImplicit {
    public void method(ClassWithCompanion c) {
        int i = c.fo<caret>o;
    }
}

// REF: (in fields.ClassWithCompanion.Companion).foo
// CLS_REF: (in fields.ClassWithCompanion.Companion).foo