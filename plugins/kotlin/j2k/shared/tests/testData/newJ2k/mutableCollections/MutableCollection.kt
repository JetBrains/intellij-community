class J {
    fun foo(
        l1: MutableCollection<Double?>,
        l2: MutableCollection<Double?>,
        l3: MutableCollection<Double?>,
        l4: MutableCollection<Double?>,
        l5: MutableCollection<Double?>,
        l6: MutableCollection<Double?>,
        l7: MutableCollection<Double?>
    ) {
        l1.add(1.0)
        l2.addAll(l3)
        l3.clear()
        l4.remove(1.0)
        l5.removeAll(l1)
        l6.removeIf { o: Double? -> l2.contains(o) }
        l7.retainAll(l2)
    }
}
