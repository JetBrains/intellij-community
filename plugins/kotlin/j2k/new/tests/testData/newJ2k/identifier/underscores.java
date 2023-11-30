import java.util.function.Function;

public class J {
    public static void main(String[] args) {
        int ____ = 0;
        int in_the_middle = 1;
        int _prefix = 2;
        test(__ -> "");
        test(__ -> __);
    }

    static void test(Function<String, String> function) {}
}