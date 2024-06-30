internal class Outer<T : Outer.Nested?>(nested: Nested?) : Comparable<Outer.Nested?> {
    override fun compareTo(o: Nested?): Int {
        return 0
    }

    internal class Nested(p: Int)
}
