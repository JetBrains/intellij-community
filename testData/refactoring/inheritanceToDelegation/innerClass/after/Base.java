public class Base {
    protected int baseField;
    public Base() {
    }
    public Base(int i) {
        baseField = i;
    }
    public void methodToOverride() {
        System.out.println("Hello from Base");
    }
}