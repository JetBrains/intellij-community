// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
class J {
    fun foo(list: ArrayList<String?>) {
        for (s in list) {
            println(s.length)
            println(s.length)
            println(s.length)
        }
    }
}
