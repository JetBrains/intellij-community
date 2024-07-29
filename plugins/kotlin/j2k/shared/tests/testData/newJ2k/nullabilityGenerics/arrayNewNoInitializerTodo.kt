// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
internal class ArrayField {
    fun test() {
        val array = arrayOfNulls<String>(0)

        for (s in array) {
            println(s.length)
        }
    }
}
