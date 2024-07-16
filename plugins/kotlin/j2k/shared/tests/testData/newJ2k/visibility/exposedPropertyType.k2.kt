// ERROR: Property 'public' exposes its 'internal' type 'B'.
import J.B

class J {
    var b: B? = null

    internal class B
}
