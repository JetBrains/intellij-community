// ERROR: 'fun <T> MutableList<T>.sort(comparator: Comparator<in T>): Unit' is deprecated. Use sortWith(comparator) instead.
// ERROR: Type argument is not within its bounds: type parameter 'T (of fun <T : Comparable<in T!>!> naturalOrder)' must be subtype of 'Comparable<in Double?>!', but actual: 'Double?'.
class J {
    var natural: Comparator<Double?> = Comparator.naturalOrder<Double?>()

    fun foo(
        l1: MutableList<Double?>,  // KTIJ-29149
        l2: MutableList<Double>,
        l3: MutableList<Double>,
        l4: MutableList<Double>,
        l5: MutableList<Double?>,
        l6: MutableList<Double?>,
        l7: MutableList<Double?>,
        l8: MutableList<Double?>,
        l9: MutableList<Double?>,
        l10: MutableList<Double?>,
        l11: MutableList<Double?>,
        l12: MutableList<Double?>,
        l13: MutableList<Double?>,
        l14: MutableList<Double>
    ) {
        l1.sort(natural)
        l2.add(1.0)
        l3.addFirst(1.0)
        l4.addLast(1.0)
        l5.addAll(l3)
        l6.clear()
        l7.removeAt(0)
        l8.remove(1.0)
        l9.removeAll(l1)
        l10.removeFirst()
        l11.removeLast()
        l12.replaceAll { n: Double? -> n!! * n }
        l13.retainAll(l13)
        l14.set(0, 1.0)
    }
}
