package one.two;

import one.two.KotlinClass.Named.NestedObject;

public class UsageWithInstance {
    void t() {
        NestedObject.INSTANCE.staticExtension(new Receiver(), 4);
    }
}