class TestInlineMethod {
    public <caret>TestInlineMethod(String s1, int r1, String s2, int r2) {
        this(Integer.valueOf(s1, r1), Integer.valueOf(s2, r2));
    }

    public TestInlineMethod(Integer i1, Integer i2) {
    }

    public static void main(String[] args) {
        TestInlineMethod test = new TestInlineMethod("10", 10, "A", 16);
    }
}