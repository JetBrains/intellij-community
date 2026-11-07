internal class A {
    private val list1 = ArrayList<String>()
    val list2: MutableList<String> = ArrayList<String>()
    val list3: MutableList<String> = ArrayList<String>()

    fun getList1(): MutableList<String> {
        return list1
    }

    fun foo() {
        list1.add("a")
        list2.add("a")
        list3.add("a")
    }
}
