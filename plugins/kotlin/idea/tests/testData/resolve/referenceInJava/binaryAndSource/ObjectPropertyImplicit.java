import fields.ObjectWithProperties;

public class ObjectPropertyImplicit {
    void method(ObjectWithProperties o) {
        int p = o.propert<caret>y;
    }
}

// REF: (in fields.ObjectWithProperties).property
// CLS_REF: (in fields.ObjectWithProperties).property