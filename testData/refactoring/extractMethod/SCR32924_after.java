public class Test {
    void m() {
        Object x = null;
        System.out.println("x = " + newMethod(x)); // [...] = selection
    }

    private Object newMethod(Object x) {
        return x;
    }
}