object C {
    fun consume1(c: C?) {
    }

    fun consume2(c: C?) {
    }

    fun foo(cList: MutableList<C?>) {
        val iter: MutableIterator<*> = cList.iterator()
        while (iter.hasNext()) {
            val c = iter.next() as C?
            consume1(c)
            consume2(c)
        }
    }
}
