import fields.ClassWithCompanion;

public class ClassFieldExplicit {
    public void method(ClassWithCompanion c) {
        String str = c.fo<caret>o$1;
    }
}

// REF: (in fields.ClassWithCompanion).foo
// CLS_REF: (in fields.ClassWithCompanion).foo