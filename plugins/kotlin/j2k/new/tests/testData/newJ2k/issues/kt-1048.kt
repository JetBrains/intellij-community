// ERROR: The integer literal does not conform to the expected type CapturedType(*)
// ERROR: The integer literal does not conform to the expected type CapturedType(*)
// ERROR: Type argument is not within its bounds: should be subtype of 'String?'
internal class G<T : String?>(t: T)
class Java {
    fun test() {
        val m: HashMap<*, *> = HashMap<Any?, Any?>()
        m[1] = 1
    }

    fun test2() {
        val m: HashMap<*, *> = HashMap<Any?, Any?>()
        val g: G<*> = G<Any?>("")
        val g2 = G("")
    }
}