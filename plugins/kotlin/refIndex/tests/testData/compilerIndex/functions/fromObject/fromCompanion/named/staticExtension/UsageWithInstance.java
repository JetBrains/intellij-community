package one.two;

public class UsageWithInstance {
    void t() {
        KotlinClass.Named.staticExtension(new Receiver(), 4);
    }
}