public class J {
    private S s = new S();

    void test() {
        s.prop = "";
    }

    static class S {
        private String prop;
    }
}