// ERROR: MEMBER_PROJECTED_OUT
// ERROR: MEMBER_PROJECTED_OUT
// ERROR: UPPER_BOUND_VIOLATED
internal class G<T : String?>(t: T?)

class Java {
    fun test() {
        val m: HashMap<*, *> = HashMap<Any?, Any?>()
        m.put(1, 1)
    }

    fun test2() {
        val m: HashMap<*, *> = HashMap<Any?, Any?>()
        val g: G<*> = G<Any?>("")
        val g2 = G<String?>("")
    }
}
