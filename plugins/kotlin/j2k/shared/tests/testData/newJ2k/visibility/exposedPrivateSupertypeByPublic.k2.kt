// ERROR: Subclass 'public' exposes its 'private-in-class' supertype 'B'.
import J.B

class J {
    private open class B
    class C : B()
}
