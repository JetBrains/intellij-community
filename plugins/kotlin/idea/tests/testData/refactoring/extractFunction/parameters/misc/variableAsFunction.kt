// PARAM_TYPES: A
// PARAM_DESCRIPTOR: val foo: A defined in testProp
class A {
    fun invoke() = 20
}
// SIBLING:
fun testProp() {
    val foo = A()
    <selection>foo()</selection>
}