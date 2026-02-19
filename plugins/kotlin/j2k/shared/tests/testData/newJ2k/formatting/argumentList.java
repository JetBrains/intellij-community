class J {
    private void test() {
        String message = B.vararg(
                "first",
                "second"
        );
    }
}

class B {
    public static String vararg(String key, Object... params) {
        return "";
    }
}