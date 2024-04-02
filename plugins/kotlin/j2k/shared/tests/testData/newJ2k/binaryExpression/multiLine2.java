public class A {
    public void mixedOperators() {
        int x = 1
                + 1 - 2;

        int x2 = 1 + 1
                 - 2;

        int x3 = 1
                 + 1
                 - 2
                 + 3;

        int x4 = 1
                 + 1
                 - 2 + 3;

        int x5 = 1
                 + 1 - 2
                 + 3;
    }

    public void nestedParentheses() {
        int x = (1
                + 1) - 2;

        int x2 = (1 + 1)
                 - 2;

        int x3 = (1
                 + (1
                 - 2))
                 + 3;

        int x4 = 1
                 + ((1
                 - 2) + 3);

        int x5 = (1
                 + ((1 - 2)
                 + 3));
    }
}