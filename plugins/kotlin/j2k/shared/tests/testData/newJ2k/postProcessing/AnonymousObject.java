public class A {
    void foo() {
        invokeLater(new Runnable() {
            @Override
            public void run() {
                System.out.println("a");
            }
        });
    }

    public static void invokeLater(Runnable doRun) {
    }
}
