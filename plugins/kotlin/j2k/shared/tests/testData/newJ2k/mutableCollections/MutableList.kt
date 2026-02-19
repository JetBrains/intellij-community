// ERROR: Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:  public fun <T> Array<out TypeVariable(T)>.sort(): Unit defined in kotlin.collections public inline fun <T : Comparable<TypeVariable(T)>> Array<out TypeVariable(T)>.sort(): Unit defined in kotlin.collections public fun <T> Array<out TypeVariable(T)>.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun <T : Comparable<TypeVariable(T)>> Array<out TypeVariable(T)>.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun ByteArray.sort(): Unit defined in kotlin.collections public fun ByteArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun CharArray.sort(): Unit defined in kotlin.collections public fun CharArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun DoubleArray.sort(): Unit defined in kotlin.collections public fun DoubleArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun FloatArray.sort(): Unit defined in kotlin.collections public fun FloatArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun IntArray.sort(): Unit defined in kotlin.collections public fun IntArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun LongArray.sort(): Unit defined in kotlin.collections public fun LongArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun ShortArray.sort(): Unit defined in kotlin.collections public fun ShortArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun UByteArray.sort(): Unit defined in kotlin.collections public fun UByteArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun UIntArray.sort(): Unit defined in kotlin.collections public fun UIntArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun ULongArray.sort(): Unit defined in kotlin.collections public fun ULongArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun UShortArray.sort(): Unit defined in kotlin.collections public fun UShortArray.sort(fromIndex: Int = ..., toIndex: Int = ...): Unit defined in kotlin.collections public fun <T : Comparable<TypeVariable(T)>> MutableList<TypeVariable(T)>.sort(): Unit defined in kotlin.collections public inline fun <T> MutableList<TypeVariable(T)>.sort(comparison: (TypeVariable(T), TypeVariable(T)) -> Int): Unit defined in kotlin.collections public inline fun <T> MutableList<TypeVariable(T)>.sort(comparator: kotlin.Comparator<in TypeVariable(T)> /* = java.util.Comparator<in TypeVariable(T)> */): Unit defined in kotlin.collections
class J {
    var natural: Comparator<Double> = Comparator.naturalOrder()

    fun foo(
        l1: List<Double?>,  // KTIJ-29149
        l2: MutableList<Double?>,
        l3: MutableList<Double?>,
        l4: MutableList<Double?>,
        l5: MutableList<Double?>,
        l6: MutableList<Double?>,
        l7: MutableList<Double?>,
        l8: MutableList<Double?>,
        l9: MutableList<Double?>,
        l10: MutableList<Double?>,
        l11: MutableList<Double?>,
        l12: MutableList<Double>,
        l13: MutableList<Double?>,
        l14: MutableList<Double?>
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
        l12.replaceAll { n: Double -> n * n }
        l13.retainAll(l13)
        l14[0] = 1.0
    }
}
