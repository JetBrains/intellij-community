public class A {
    public class <caret>Inner implements Runnable {
        private final int c;

        public Inner(int arg) {
            c = arg;
        }

        public void run() {
            System.out.println(c);
        }
    }

    public void test() {
        int c = 0;
        Inner i = new Inner(1);
        new Thread(i).start();
    }
}
