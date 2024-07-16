// ERROR: Subclass 'protected (in J)' exposes its 'private-in-class' supertype 'B'.
import J.B

class J {
    private open class B
    protected class C : B()
}
