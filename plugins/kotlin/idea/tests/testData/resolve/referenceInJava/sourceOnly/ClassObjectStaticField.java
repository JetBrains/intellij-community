import k.StaticFieldInClassObjectInInterface;

public class ClassObjectField {
    public static void foo() {
        Object object = StaticFieldInClassObjectInInterface.<caret>XX;
    }
}

// REF: (in k.StaticFieldInClassObjectInInterface.Companion).XX