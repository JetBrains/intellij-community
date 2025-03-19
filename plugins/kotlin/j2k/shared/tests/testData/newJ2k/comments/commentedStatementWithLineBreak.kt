internal object Foo {
    fun test1() {
        val a = 1
        // comment
        if (a == 0) {
            println("")
        }
        // comment 2
        val b = 2

        // comment 3
        val c = 3

        // comment 4
        val d = 4

        // comment 5
        println("")
    }

    fun test2() {
        val a = 1
        /* comment */
        if (a == 0) {
            println("")
        }
        /* comment 2 */
        val b = 2

        /* comment 3 */
        val c = 3

        /* comment 4 */
        val d = 4

        /* comment 5 */
        println("")
    }

    fun test3() {
        val a = 1
        /*
         * comment
         * comment
         */
        if (a == 0) {
            println("")
        }
        /*
         * comment 2
         * comment 2
         */
        val b = 2

        /*
         * comment 3
         * comment 3
         */
        val c = 3

        /*
         * comment 4
         * comment 4
         */
        val d = 4

        /*
         * comment 5
         * comment 5
         */
        println("")
    }
}
