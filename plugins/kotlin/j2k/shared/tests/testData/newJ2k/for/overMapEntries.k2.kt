internal object Test {
    @JvmStatic
    fun main(args: Array<String>) {
        val resultMap: MutableMap<String, String?> = HashMap<String, String?>()
        for (entry in resultMap.entries) {
            val key = entry.key
            val type = entry.value

            if (key == "myKey") {
                println(type)
            }
        }
    }
}
