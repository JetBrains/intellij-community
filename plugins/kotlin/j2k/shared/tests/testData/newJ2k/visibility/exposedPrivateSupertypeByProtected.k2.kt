// ERROR: 'protected (in J)' subclass exposes its 'private-in-class' supertype 'B'.
class J {
    private open class B
    protected class C : B()
}
