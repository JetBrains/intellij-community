import fields.ObjectWithProperties;

public class ObjectPropertyExplicit {
    void method() {
        int p = ObjectWithProperties.propert<caret>y;
    }
}

// REF: (in fields.ObjectWithProperties).property
// CLS_REF: (in fields.ObjectWithProperties).property