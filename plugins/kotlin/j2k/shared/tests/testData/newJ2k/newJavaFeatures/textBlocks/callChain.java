public class J {
    public static void main(String[] args) {
        foo("""
                John Q. Smith""".substring(8).equals("Smith"));
    }
    static void foo(boolean b) {}
}