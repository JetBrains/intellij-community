internal object J {
    fun <T> foo(a: Array<T>): Array<T> {
        return a
    }

    fun <T> twoDimGenericArray(arr: Array<Array<T>>): Array<Array<T>> {
        return arr
    }

    fun arrayOfLists(arr: Array<MutableList<String>?>?): Array<MutableList<String>?>? {
        return arr
    }

    fun listOfNotNullArrays(list: List<Array<String>>?): List<Array<String>>? {
        return list
    }
}
