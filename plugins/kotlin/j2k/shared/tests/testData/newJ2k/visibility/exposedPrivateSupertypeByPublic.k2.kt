// ERROR: 'public' subclass exposes its 'private-in-class' supertype 'B'.
class J {
    private open class B
    class C : B()
}
