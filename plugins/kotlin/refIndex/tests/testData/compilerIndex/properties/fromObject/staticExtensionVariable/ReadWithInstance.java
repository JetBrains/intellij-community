package one.two;

public class ReadWithInstance {
    public static void main(String[] args) {
        int i = KotlinObject.INSTANCE.getStaticExtensionVariable(42);
    }
}