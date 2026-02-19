public class J {
    public void test1() {
        int lastIndex = 5;
        for (int i = 0; i < lastIndex; i++) {
            lastIndex--;
            System.out.println(lastIndex);
        }
    }

    public void test2() {
        int lastIndex = 5;
        for (int i = 0; i < (1 + 2 + lastIndex); i++) {
            lastIndex--;
            System.out.println(lastIndex);
        }
    }
}