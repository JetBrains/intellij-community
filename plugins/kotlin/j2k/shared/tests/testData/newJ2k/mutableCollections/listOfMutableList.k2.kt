class Test {
    var list: MutableList<MutableList<Int>?> = ArrayList<MutableList<Int>?>()

    fun test() {
        list.get(0)!!.add(1)
    }
}
