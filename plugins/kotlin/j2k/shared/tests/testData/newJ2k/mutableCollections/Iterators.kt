internal class A {
    fun setThroughListIterator(list: MutableList<String>) {
        val iterator = list.listIterator()
        iterator.next()
        iterator.set("a")
    }

    fun removeInLoop(list: MutableList<String?>) {
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            if (iterator.next()!!.isEmpty()) iterator.remove()
        }
    }

    fun readInLoop(list: List<String?>) {
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            println(iterator.next())
        }
    }

    fun removeThroughReturnedIterator(list: MutableList<String?>) {
        val iterator = iteratorOf(list)
        iterator.next()
        iterator.remove()
    }

    private fun iteratorOf(list: MutableList<String?>): MutableIterator<String?> {
        return list.iterator()
    }
}
