import fields.FieldsKt;

public class FacadeProperty {
    void method() {
        int p = FieldsKt.topLevel<caret>Property;
    }
}

// REF: (fields).topLevelProperty
// CLS_REF: (fields).topLevelProperty