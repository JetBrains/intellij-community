import fields.ClassJvmField;

public class ClassCompanionJvmField {
    public void method(ClassJvmField c) {
        String str = c.fo<caret>o;
    }
}

// REF: (in fields.ClassJvmField).foo
// CLS_REF: (in fields.ClassJvmField).foo

// REF_K1: (in fields.ClassJvmField.Companion).foo
