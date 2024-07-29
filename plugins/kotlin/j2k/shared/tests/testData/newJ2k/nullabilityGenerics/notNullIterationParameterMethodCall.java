import java.util.*;

class TestMethodCall {
    public void test() {
        for (String s : returnStrings()) {
            System.out.println(s.length());
        }
    }

    private Iterable<String> returnStrings() {
        return new ArrayList<>();
    }
}