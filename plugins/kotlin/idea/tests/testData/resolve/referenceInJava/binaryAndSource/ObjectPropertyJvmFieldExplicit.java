import fields.ObjectWithProperties;

public class ObjectPropertyJvmFieldExplicit {
    void method() {
        String s = ObjectWithProperties.propert<caret>yWithJvmField;
    }
}

// REF: (in fields.ObjectWithProperties).propertyWithJvmField
// CLS_REF: (in fields.ObjectWithProperties).propertyWithJvmField