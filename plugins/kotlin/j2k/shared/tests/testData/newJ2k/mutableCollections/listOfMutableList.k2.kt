class Test {
    var list: List<MutableList<Int>?> = ArrayList<MutableList<Int>?>()

    fun test() {
        list.get(0)!!.add(1)
    }
}
