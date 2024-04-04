public class A {
    public static void main(String[] args) {
        int x = 1 // comment
                + 1 - 2; // comment 2

        int x2 = 1 + 1 // comment
                - 2; // comment 2

        int x3 = 1 // comment
                 + 1 // comment 2
                 - 2 // comment 3
                 + 3; // comment 4

        int x4 = 1 // comment
                 + 1 // comment 2
                 - 2 + 3; // comment 3

        int x5 = 1 // comment
                 + 1 - 2 // comment 2
                 + 3; // comment 3
    }
}