public class J {
    public static void main(String[] args) {
        // escaped backslashes are balanced in pairs
        String backslash = """
                \\
                \\\\
                """;

        // \' and ' are the same thing inside a text block (\' is an "unnecessary escape")
        // in Kotlin they are both translated to just '
        String quote = """
                \'
                \\'
                \\\'
                \\\\'
                """;

        // same thing with double quote
        String doubleQuote = """
                \"
                \\"
                \\\"
                \\\\"
                """;

        // an even number of leading backslashes prevents the char escaping
        // it is translated to a regular character ('r', 't', etc.)
        String charEscapes = """                         
                \r
                \\r
                \\\r
                \\\\r
                                
                \t
                \\t
                \\\t
                \\\\t
                                
                \b
                \\b
                \\\b
                \\\\b
                                
                \f
                \\f
                \\\f
                \\\\f
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