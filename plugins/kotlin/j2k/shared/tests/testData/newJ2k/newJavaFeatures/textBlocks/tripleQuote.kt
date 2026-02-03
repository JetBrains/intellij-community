object J {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = """
    String source = ${'"'}""
        String message = "Hello, World!";
        System.out.println(message);
        ""${'"'};

    """.trimIndent()
    }
}
