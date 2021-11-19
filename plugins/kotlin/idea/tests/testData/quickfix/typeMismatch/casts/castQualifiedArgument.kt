// "Cast expression 'a.foo().single()' to 'Int'" "true"
// WITH_STDLIB
class A {
    fun foo(): List<Any> = listOf()

    fun bar(i : Int, s: String) = Unit

    fun use() {
        val a = A()
        a.bar(a.foo().single<caret>(), "Asd")
    }
}