class Foo {
    public static void test1() {
        int a = 1;
        // comment
        if (a == 0) {
            System.out.println("");
        }
        // comment 2
        int b = 2;

        // comment 3
        int c = 3;

        // comment 4

        int d = 4;

        // comment 5
        System.out.println("");
    }

    public static void test2() {
        int a = 1;
        /* comment */
        if (a == 0) {
            System.out.println("");
        }
        /* comment 2 */
        int b = 2;

        /* comment 3 */
        int c = 3;

        /* comment 4 */

        int d = 4;

        /* comment 5 */
        System.out.println("");
    }

    public static void test3() {
        int a = 1;
        /*
         * comment
         * comment
         */
        if (a == 0) {
            System.out.println("");
        }
        /*
         * comment 2
         * comment 2
         */
        int b = 2;

        /*
         * comment 3
         * comment 3
         */
        int c = 3;

        /*
         * comment 4
         * comment 4
         */

        int d = 4;

        /*
         * comment 5
         * comment 5
         */
        System.out.println("");
    }
}