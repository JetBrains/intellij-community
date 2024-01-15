// ERROR: Unresolved reference: ELinkedList
class ForEach {
    fun test() {
        val xs = ArrayList<Any>()
        val ys: MutableList<Any> = ELinkedList<Any>()
        for (x in xs) {
            ys.add(x)
        }
        for (y in ys) {
            xs.add(y)
        }
    }
}