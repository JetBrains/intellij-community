object A {
    @JvmStatic
    fun main(args: Array<String>) {
        val x = (1 // comment
                + 1 - 2) // comment 2

        val x2 = (1 + 1 // comment
                - 2) // comment 2

        val x3 = (1 // comment
                + 1 // comment 2
                - 2 // comment 3
                + 3) // comment 4

        val x4 = (1 // comment
                + 1 // comment 2
                - 2 + 3) // comment 3

        val x5 = (1 // comment
                + 1 - 2 // comment 2
                + 3) // comment 3
    }
}
