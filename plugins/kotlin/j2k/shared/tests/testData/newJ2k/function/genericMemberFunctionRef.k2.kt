class TestLambda<T> {
    class Box<Q>(private val value: Q?) {
        fun unbox(): Q? {
            return value
        }
    }

    fun toStringAllBox(list: MutableList<Box<T?>?>): String {
        return list.stream().map<T?> { obj: Box<T?>? -> obj!!.unbox() }.map<String> { obj: T? -> obj.toString() }
            .reduce { s1: String?, s2: String? -> s1 + ", " + s2 }.orElse("")
    }
}
