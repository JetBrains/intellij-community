internal class A {
    fun changedThroughPattern(items: Collection<String?>?) {
        if (items is MutableList<String?>) {
            items.add("a")
        }
    }

    fun readThroughPattern(items: Collection<String?>?): Int {
        if (items is MutableList<String?>) {
            return items.size
        }
        return 0
    }
}
