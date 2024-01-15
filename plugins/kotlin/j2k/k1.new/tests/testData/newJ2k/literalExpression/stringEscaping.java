public class A {
    // ascii escapes
    String ascii1 = "\t\b\n\r\'\"\\";
    String ascii2 = "\f";
    String ascii3 = "\\f";
    String ascii4 = "\\\f";

    // backslash
    String backslash1 = "\1";
    String backslash2 = "\\1";
    String backslash3 = "\\\1";
    String backslash4 = "\\\\1";
    String backslash5 = "\\\\\1";
    String backslash6 = "\1\1";

    // dollar
    String dollar1 = "$a";
    String dollar2 = "$A";
    String dollar3 = "${s}";
    String dollar4 = "$$";

    // octal
    String octal1 = "\1\0\1\1\1\2\1\3\1\4";
    String octal2 = "\1\0\1\1\1\2\1\3\1\4" + "\1\15\1\14\1\15\2\1\1";
    String octal3 = "\770123";
    String octal4 = "\1110123";
    String octal5 = "\3770123";
    String octal6 = "\000Text";
    String octal7 = "\09";
}