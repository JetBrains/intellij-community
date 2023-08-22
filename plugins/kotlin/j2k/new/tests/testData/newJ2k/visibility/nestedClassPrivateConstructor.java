public class J {
    private static class Marker {
        private int x = 42;
        private Marker() {}
    }

    public Object foo() {
        return new Marker();
    }
}