internal class J {
    fun simple(list: List<String>?): List<String>? {
        return list
    }

    fun nested(map: Map<String?, MutableList<String>?>?): Map<String?, MutableList<String>?>? {
        return map
    }

    fun wildcard(collection: Collection<out String>?): Collection<out String>? {
        return collection
    }

    fun nestedNotNull(list: List<MutableList<String>?>?): List<MutableList<String>?>? {
        return list
    }

    fun nestedNullable(list: List<MutableList<String?>?>?): List<MutableList<String?>?>? {
        return list
    }

    fun outerNullableInnerNotNull(list: List<MutableList<String>?>?): List<MutableList<String>?>? {
        return list
    }

    fun outerNotNullInnerNullable(list: List<MutableList<String?>>?): List<MutableList<String?>>? {
        return list
    }

    fun mapMixed(map: Map<String, MutableList<String>?>?): Map<String, MutableList<String>?>? {
        return map
    }

    fun <T> notNullT(list: List<T>?): List<T>? {
        return list
    }

    fun <T> nullableT(list: List<T?>?): List<T?>? {
        return list
    }

    fun <T> nestedNotNullT(list: List<MutableList<T>?>?): List<MutableList<T>?>? {
        return list
    }

    fun <T> outerNullableInnerNotNullT(list: List<MutableList<T>?>?): List<MutableList<T>?>? {
        return list
    }
}
