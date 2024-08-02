// ERROR: Function 'public' exposes its 'internal' parameter type 'B'.
class J {
    internal class B

    fun test(b: B?) {
    }
}
