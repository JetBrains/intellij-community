import fields.CompanionJvmField;

public class CompanionJvmFieldImplicit {
    public void method(CompanionJvmField c) {
        int i = c.fo<caret>o;
    }
}

// REF: (in fields.CompanionJvmField.Companion).foo
// CLS_REF: (in fields.CompanionJvmField.Companion).foo