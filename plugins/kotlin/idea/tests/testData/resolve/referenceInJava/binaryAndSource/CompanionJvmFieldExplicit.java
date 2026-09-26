import fields.CompanionJvmField;

public class CompanionJvmFieldExplicit {
    public void method() {
        int i = CompanionJvmField.fo<caret>o;
    }
}

// REF: (in fields.CompanionJvmField.Companion).foo
// CLS_REF: (in fields.CompanionJvmField.Companion).foo