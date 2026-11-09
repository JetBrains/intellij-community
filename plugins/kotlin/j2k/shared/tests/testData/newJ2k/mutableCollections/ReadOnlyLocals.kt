internal class A {
    fun readOnly(items: Collection<String?>): Int {
        val copy: List<String?> = ArrayList<String?>(items)
        return copy.size
    }

    fun mutated() {
        val list: MutableList<String> = ArrayList<String>()
        list.add("a")
    }

    fun returned(): MutableList<String?> {
        val list: MutableList<String?> = ArrayList<String?>()
        return list
    }

    fun passedToMutatingParameter() {
        val list: MutableList<String> = ArrayList<String>()
        add(list)
    }

    fun add(list: MutableList<String>) {
        list.add("a")
    }

    fun iterated(items: Collection<String?>) {
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            iterator.next()
        }
    }

    fun removedByIterator(items: MutableCollection<String?>) {
        val iterator = items.iterator()
        iterator.next()
        iterator.remove()
    }
}
