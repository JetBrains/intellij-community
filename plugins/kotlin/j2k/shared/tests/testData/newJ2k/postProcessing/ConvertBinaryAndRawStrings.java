// IGNORE_K2
public class Test {
    String s1 = """
               asdf
               asdfasdf""";
    String s2 = "asdf\n" +
                "asdfasdf\n";
    String s3 = "asdf\n" +
                "nadfadsf\n" +
                "asdfasdf";
    String s4 = "asdf\n" +
                "nadfadsf\n" +
                "asdfasdf\n";
    String trailingNewlinesOnly = "asdf" + "\n";
    String trailingNewlinesOnly2 = "asdf\n" + "\n";
}