// ERROR: The integer literal does not conform to the expected type CapturedType(*)
// ERROR: The integer literal does not conform to the expected type CapturedType(*)
internal class G<T : String?>(t: T)

class Java {
    fun test() {
        val m: HashMap<*, *> = HashMap<Any?, Any?>()
        m[1] = 1
    }

    fun test2() {
        val m: HashMap<*, *> = HashMap<Any?, Any?>()
        val g: G<*> = G("")
        val g2 = G("")
    }
}
