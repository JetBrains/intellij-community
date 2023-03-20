public class J {
    public static void main(String[] args) {
        // should strip two trailing spaces
        String s = """
                red  
                """;

        // should preserve escaped trailing spaces
        // an even number of leading backslashes prevents the escaping
        String s2 = """
                trailing\040\040
                trailing\\040
                trailing\\\040
                trailing\\\\040
                """;

        // \s is the same as \040
        String colors = """
                trailing\s
                trailing\\s
                trailing\\\s
                trailing\\\\s
                """;
    }
}