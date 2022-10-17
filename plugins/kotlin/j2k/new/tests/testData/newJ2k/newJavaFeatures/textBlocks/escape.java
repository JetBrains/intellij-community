public class J {
    public static void main(String[] args) {
        String escapes = """
                \\
                \'
                \"
                \r
                \t
                \b
                \f
                """;
        String newlines = """
                foo\nbar\nbaz
                """;
        String suppressNewlines = """
              This is \
              a single \
              line.\
              """;
    }
}