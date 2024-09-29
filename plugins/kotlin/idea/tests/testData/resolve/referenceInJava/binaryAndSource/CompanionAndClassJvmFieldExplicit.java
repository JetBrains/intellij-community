import fields.ClassAndCompanionJvmField;

public class CompanionAndClassJvmFieldExplicit {
    public void method() {
        int i = ClassAndCompanionJvmField.fo<caret>o;
    }
}

// REF: (in fields.ClassAndCompanionJvmField.Companion).foo
// CLS_REF: (in fields.ClassAndCompanionJvmField).foo
// ^should be companion object, but for some reasons Java resolves into the class