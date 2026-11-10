internal class J {
    fun simple(list: List<String>?): List<String>? {
        return list
    }

    fun nested(map: Map<String?, List<String>?>?): Map<String?, List<String>?>? {
        return map
    }

    fun wildcard(collection: Collection<out String>?): Collection<out String>? {
        return collection
    }

    fun nestedNotNull(list: List<List<String>?>?): List<List<String>?>? {
        return list
    }

    fun nestedNullable(list: List<List<String?>?>?): List<List<String?>?>? {
        return list
    }

    fun outerNullableInnerNotNull(list: List<List<String>?>?): List<List<String>?>? {
        return list
    }

    fun outerNotNullInnerNullable(list: List<List<String?>>?): List<List<String?>>? {
        return list
    }

    fun mapMixed(map: Map<String, List<String>?>?): Map<String, List<String>?>? {
        return map
    }

    fun <T> notNullT(list: List<T>?): List<T>? {
        return list
    }

    fun <T> nullableT(list: List<T?>?): List<T?>? {
        return list
    }

    fun <T> nestedNotNullT(list: List<List<T>?>?): List<List<T>?>? {
        return list
    }

    fun <T> outerNullableInnerNotNullT(list: List<List<T>?>?): List<List<T>?>? {
        return list
    }
}
