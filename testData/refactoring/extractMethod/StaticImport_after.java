import static java.lang.Integer.MAX_VALUE;

class StaticImport {
    void test() {
        newMethod();
    }

    private void newMethod() {
        System.out.println(MAX_VALUE);
    }
}