import fields.CompanionJvmField;

public class ClassCompanionJvmField {
    public void method(CompanionJvmField c) {
        String str = c.fo<caret>o$1;
    }
}

// REF: (in fields.CompanionJvmField).foo
// CLS_REF: (in fields.CompanionJvmField).foo