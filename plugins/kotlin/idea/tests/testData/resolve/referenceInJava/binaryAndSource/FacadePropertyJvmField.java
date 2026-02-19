import fields.FieldsKt;

public class FacadeProperty {
    void method() {
        String s = FieldsKt.topLevelPropertyW<caret>ithJvmField;
    }
}

// REF: (fields).topLevelPropertyWithJvmField
// CLS_REF: (fields).topLevelPropertyWithJvmField