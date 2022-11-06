object J {
    @JvmStatic
    fun main(args: Array<String>) {
        val s1 = "\u0001\u0000\u0001\u0001\u0001\u0002\u0001\u0003\u0001\u0004"
        val s2 =
            "\u0001\u0000\u0001\u0001\u0001\u0002\u0001\u0003\u0001\u0004" + "\u0001\u000d\u0001\u000c\u0001\u000d\u0002\u0001\u0001"
    }
}
