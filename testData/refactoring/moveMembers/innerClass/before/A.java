public class A {
    public static class Inner {
        public boolean equals(Object o) {
            return o instanceof Inner;
        }
    }
}