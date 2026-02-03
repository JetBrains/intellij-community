public class J {
    public class Z {
        private static final int MAGIC = 42;
    }

    public class A {
        public int foo() { return Z.MAGIC; }
    }
}