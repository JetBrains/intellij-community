package one.two;

import one.two.KotlinClass.Companion.NestedObject;

public class Usage {
    void t() {
        NestedObject.INSTANCE.extension(new Receiver(), 4);
    }
}