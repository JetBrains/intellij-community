public class J {
    public void test() {
        Pojo pojo = new Pojo("a", "b");
        doSomething(pojo.getFieldB());
    }

    private void doSomething(String myString) {
    }
}