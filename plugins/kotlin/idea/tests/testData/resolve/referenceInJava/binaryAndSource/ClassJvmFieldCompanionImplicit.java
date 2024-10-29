import fields.ClassJvmField;

public class ClassJvmFieldCompanionImplicit {
    public void method(ClassJvmField c) {
        int i = c.fo<caret>o$1;
    }
}

// REF: (in fields.ClassJvmField.Companion).foo
// CLS_REF: (in fields.ClassJvmField.Companion).foo

// REF_K1: (in fields.ClassJvmField).foo