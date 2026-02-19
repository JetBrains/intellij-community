import fields.ObjectWithProperties;

public class ObjectPropertyJvmFieldImplicit {
    void method(ObjectWithProperties o) {
        String s = o.propert<caret>yWithJvmField;
    }
}

// REF: (in fields.ObjectWithProperties).propertyWithJvmField
// CLS_REF: (in fields.ObjectWithProperties).propertyWithJvmField