public class C {
    private String x = "";
    C other = null;

    void test(C c) {
        if (c.other != null) {
            c.other.x = "";
        }
    }
}