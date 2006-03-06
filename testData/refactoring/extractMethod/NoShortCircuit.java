public class Test {
    public static void foo(char c) {
        if (<selection>c == '\n' || c == '\r'</selection> || c == '\u0000') {
            System.out.println("");
        }
    }
}
