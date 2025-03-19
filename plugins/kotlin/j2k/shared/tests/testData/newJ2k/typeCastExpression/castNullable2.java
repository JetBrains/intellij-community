// IGNORE_K1
public class TestCastAndCheckForNull {
    public String foo() {
        String s = (String) bar();
        if (s == null) return "null";
        return s;
    }

    public Object bar() {
        return "abc";
    }
}