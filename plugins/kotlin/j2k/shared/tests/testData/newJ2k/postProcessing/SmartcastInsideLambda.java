public class J {
    private String s = null;

    void foo() {
        String local = s;
        if (local == null) {
            synchronized (this) {
                local = "local";
                System.out.println(local.length());
            }
        }
    }
}
