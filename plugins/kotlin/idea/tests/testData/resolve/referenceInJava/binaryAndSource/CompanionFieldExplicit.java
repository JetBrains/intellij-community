import fields.ClassWithCompanion;

public class CompanionFieldExplicit {
    public void method() {
        int i = ClassWithCompanion.fo<caret>o;
    }
}

// REF: (in fields.ClassWithCompanion.Companion).foo
// CLS_REF: (in fields.ClassWithCompanion.Companion).foo