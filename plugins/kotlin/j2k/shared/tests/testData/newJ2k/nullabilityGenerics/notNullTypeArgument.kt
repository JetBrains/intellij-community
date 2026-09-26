internal class J {
    fun simple(list: MutableList<String>?): MutableList<String>? {
        return list
    }

    fun nested(map: MutableMap<String?, MutableList<String>?>?): MutableMap<String?, MutableList<String>?>? {
        return map
    }

    fun wildcard(collection: MutableCollection<out String>?): MutableCollection<out String>? {
        return collection
    }

    fun nestedNotNull(list: MutableList<MutableList<String>?>?): MutableList<MutableList<String>?>? {
        return list
    }

    fun nestedNullable(list: MutableList<MutableList<String?>?>?): MutableList<MutableList<String?>?>? {
        return list
    }

    fun outerNullableInnerNotNull(list: MutableList<MutableList<String>?>?): MutableList<MutableList<String>?>? {
        return list
    }

    fun outerNotNullInnerNullable(list: MutableList<MutableList<String?>>?): MutableList<MutableList<String?>>? {
        return list
    }

    fun mapMixed(map: MutableMap<String, MutableList<String>?>?): MutableMap<String, MutableList<String>?>? {
        return map
    }

    fun <T> notNullT(list: MutableList<T>?): MutableList<T>? {
        return list
    }

    fun <T> nullableT(list: MutableList<T?>?): MutableList<T?>? {
        return list
    }

    fun <T> nestedNotNullT(list: MutableList<MutableList<T>?>?): MutableList<MutableList<T>?>? {
        return list
    }

    fun <T> outerNullableInnerNotNullT(list: MutableList<MutableList<T>?>?): MutableList<MutableList<T>?>? {
        return list
    }
}
