// IGNORE_K2
class C {
    private String s = x();

    void foo() {
        if (s == null) {
            System.out.print("null");
        }
    }
}
