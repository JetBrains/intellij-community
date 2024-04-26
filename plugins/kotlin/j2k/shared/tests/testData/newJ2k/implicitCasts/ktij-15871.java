public class TFloat {
    private class TestFloat {
        public TestFloat(float f1) {}
    }

    public TFloat() {
        new TestFloat(42);
    }
}