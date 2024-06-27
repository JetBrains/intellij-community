public class Example {
    public void test() {
        if (o instanceof String s1) {
            System.out.println(s1);
        }

        if (this.o instanceof String s2) {
            System.out.println(s2);
        }
    }

    Object o = new Object();
}