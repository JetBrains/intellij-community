class Class {
    public static int staticMethod() {
        int a = 1;
        int b = 2;

        return newMethod(a + b);
    }

    public int foo(int a, int b) {
        return newMethod(a + b);
    }

    private static int newMethod(int i) {
        int temp = i;
        return temp * 2;
    }
}