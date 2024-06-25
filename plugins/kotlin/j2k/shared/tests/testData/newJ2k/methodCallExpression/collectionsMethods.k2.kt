internal class A {
    fun foo(): MutableMap<String?, String?> {
        val list1 = mutableListOf<String?>()
        val list2 = mutableListOf<Int?>(1)
        val set1 = mutableSetOf<String?>()
        val set2 = mutableSetOf<String?>("a")
        return mutableMapOf<String?, String?>()
    }
}
