internal class Lists {
    private val withNull: MutableList<String?> = ArrayList<String?>()
    private val clean: MutableList<String> = ArrayList<String>()

    fun fill() {
        withNull.add(null)
        clean.add("a")
        print(withNull)
        print(clean)
    }

    private fun print(items: List<out String?>) {
        println(items.size)
    }

    fun fromClean(): String {
        return clean.get(0)
    }

    fun fromWithNull(): String? {
        return withNull.get(0)
    }
}