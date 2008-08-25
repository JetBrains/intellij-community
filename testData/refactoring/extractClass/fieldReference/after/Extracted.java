public class Extracted {
    private final Test test;

    public Extracted(Test test) {
        this.test = test;
    }


    void foo() {
        if (test.getMyField() == 7) {
        }
    }
}
