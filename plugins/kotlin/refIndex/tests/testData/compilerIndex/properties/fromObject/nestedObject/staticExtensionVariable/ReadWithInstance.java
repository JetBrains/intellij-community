package one.two;

public class ReadWithInstance {
    public static void main(String[] args) {
        int i = KotlinObject.Nested.INSTANCE.getStaticExtensionVariable(42);
    }
}