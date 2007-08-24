public class IntentionTest {
    public IntentionTest() {}

    public void method( Object ... args ) {
        for (int i = 0; i < args.length; i++) {
            System.out.println("args["+i+"] = " + args[i]);
        }
    }

    public void main(String[] args) {
        String[] params = new String[]{ "0", "1" };
        method(new Object[]{params});
        method(new Object[]{"2", params});
        method(new Object[]{params, params});
    }
}