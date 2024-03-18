internal object A {
    @JvmStatic
    fun main(args: Array<String>) {
        val s = ""

        val result = s
            .replace("_", "/") + "=="

        val result2 = (s
            .replace("_", "/") // comment
                + "==")

        val result3 = s
            .replace("_", "/") + "=="

        val result4 = (s
            .replace("_", "/") + "==").replace("_", "/")

        val result5 = (s
            .replace("_", "/") + "==")
            .replace("_", "/")

        val result6 = (s
            .replace("_", "/") + "==") // comment
            .replace("_", "/")
    }
}
