public class J {
    public static void main(String[] args) {
        // should strip two trailing spaces
        String s = """
    red  
    """;

        // should preserve two escaped trailing spaces
        String s2 = """
    trailing\040\040
    white space
    """;
        // \s is the same as \040
        String colors = """
    red  \s
    green\s
    blue \s
    """;
    }
}