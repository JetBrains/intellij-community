class J {
    fun foo(
        l0: MutableSet<Int>,
        l1: MutableSet<Int?>,
        l2: MutableSet<Int?>,
        l3: MutableSet<Int?>,
        l4: MutableSet<Int?>,
        l5: Set<Int?>
    ) {
        l0.add(1)
        l1.addAll(l5)
        l2.clear()
        l3.removeAll(l5)
        l4.retainAll(l5)
        l5.contains(1)
    }
}
