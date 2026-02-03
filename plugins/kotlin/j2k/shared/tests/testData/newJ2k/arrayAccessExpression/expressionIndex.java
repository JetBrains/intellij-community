public class J {
    void test(int[] array) {
        int i = array[new J().calculateIndex()];
    }

    int calculateIndex() {
        return 0;
    }
}