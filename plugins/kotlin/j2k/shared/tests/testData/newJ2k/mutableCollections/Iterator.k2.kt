internal class IteratorTest {
    var mutableMap1: MutableMap<String?, String> = HashMap<String?, String>()
    var mutableMap2: MutableMap<String?, String?> = HashMap<String?, String?>()

    fun testFields() {
        mutableMap1.values.add("")
        mutableMap2.entries.iterator().remove()
    }

    fun testFunctionParameters(immutableCollection: MutableCollection<String?>, mutableList: MutableList<Int>) {
        val it = immutableCollection.iterator()
        while (it.hasNext()) {
            it.next()
        }
        mutableList.listIterator().add(2)
    }
}
