// ERROR: 'public' function exposes its 'internal' return type 'B'.
class J {
    internal class B

    fun test(): B {
        return B()
    }
}
