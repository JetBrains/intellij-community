import java.util.Arrays

class J {
    fun foo(a: Array<Any>) {
        a.copyOf(5)
        Arrays.copyOf(a, 5, Array<Any>::class.java) // not applicable
        // Don't convert copyOfRange, because Kotlin's copyOfRange can throw IndexOutOfBoundsException
        Arrays.copyOfRange(a, 5, 6)
        Arrays.copyOfRange(a, 5, 6, Array<Any>::class.java)
        //
        a.contentEquals(a)
        a.contentDeepEquals(a)
        //
        a.contentHashCode()
        a.contentDeepHashCode()
        //
        a.contentToString()
        a.contentDeepToString()
    }
}
