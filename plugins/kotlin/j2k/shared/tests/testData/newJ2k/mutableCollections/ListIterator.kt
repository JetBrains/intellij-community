class J {
    fun foo(
        l0: MutableListIterator<Int>,
        l1: MutableListIterator<Int?>,
        l2: MutableListIterator<Int>,
        l3: ListIterator<Int?>
    ) {
        l0.add(1)
        l1.remove()
        l2.set(1)
        l3.next()
    }
}
