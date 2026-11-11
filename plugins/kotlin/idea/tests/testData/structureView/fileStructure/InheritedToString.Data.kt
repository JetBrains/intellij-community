open class A0 {
    fun foo() {}
}
open class A : A0() {
    override fun toString(): String = super.toString()
}
interface IA {
    fun foo()
}