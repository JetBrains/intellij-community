class J {
    public static void main(String[] args) {
        // normal case: trailing comma is preserved
        int[] i = {
                1,
                2,
        };

        // all on one line: trailing comma is removed by Kotlin formatter
        int[] j = {1, 2,};

        // weird case with empty array: comma is removed (this syntax is invalid in Kotlin)
        int[][] k = {{,}, {,}};
    }
}

enum E {
    A, B,
}

enum E2 {
    A, B, ;
}

enum E3 {
    A,
    B,
}

enum E4 {
    A,
    B, ;
}

enum E5 {
    A,
    B,
    ;
}

enum E6 {
    A,
    B,;

    void foo() {}
}

enum E7 {
    A,
    B,
    ;

    void foo() {}
}