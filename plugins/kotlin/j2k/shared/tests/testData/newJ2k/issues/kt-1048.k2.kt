// ERROR: Receiver type 'HashMap<*, *>' contains star projection which prohibits the use of 'fun put(key: K, value: V): V?'.
// ERROR: Receiver type 'HashMap<*, *>' contains star projection which prohibits the use of 'fun put(key: K, value: V): V?'.
// ERROR: Type argument is not within its bounds: type parameter 'T (of class G<T : String?>)' must be subtype of 'String?', but actual: 'Any?'.
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
