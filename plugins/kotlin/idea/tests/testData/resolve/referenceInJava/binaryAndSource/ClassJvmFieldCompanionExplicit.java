import fields.ClassJvmField;

public class ClassJvmFieldCompanionExplicit {
    public void method() {
        int i = ClassJvmField.fo<caret>o$1;
    }
}

// REF: (in fields.ClassJvmField.Companion).foo
// CLS_REF: (in fields.ClassJvmField.Companion).foo

// REF_K1: (in fields.ClassJvmField).foo