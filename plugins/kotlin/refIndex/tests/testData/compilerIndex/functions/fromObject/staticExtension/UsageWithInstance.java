public class UsageWithInstance {
    void t() {
        KotlinObject.INSTANCE.staticExtension(new Receiver(), 4);
    }
}