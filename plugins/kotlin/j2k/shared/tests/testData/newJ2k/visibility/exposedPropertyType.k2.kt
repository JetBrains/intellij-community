// ERROR: 'public' property exposes its 'internal' type 'B'.
class J {
    var b: B? = null

    internal class B
}
