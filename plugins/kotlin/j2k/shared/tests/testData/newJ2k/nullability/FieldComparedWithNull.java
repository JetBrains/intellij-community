class C {
    private String s = x();

    private String x() {
        return null;
    }

    void foo() {
        if (s == null) {
            System.out.print("null");
        }
    }
}