open class A<T> {
    open fun <caret>f(t : T) {}
}
class B : A<String>() {
    override fun f(t : String) {}
}