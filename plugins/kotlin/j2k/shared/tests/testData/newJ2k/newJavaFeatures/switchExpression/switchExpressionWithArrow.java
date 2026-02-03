package test;
public class J {
    public static String foo(int x) {
        return switch (x) {
            case 0 -> "zero";
            case 1 -> "one";
            case 2 -> "two";
            default -> "many";
        };
    }
}