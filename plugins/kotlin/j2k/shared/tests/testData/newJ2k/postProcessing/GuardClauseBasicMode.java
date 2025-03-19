// !BASIC_MODE: true
public class Test {
    void testBasicMode(String s1) {
        if (s1 == null) {
            throw new IllegalArgumentException("s should not be null");
        }
    }
}