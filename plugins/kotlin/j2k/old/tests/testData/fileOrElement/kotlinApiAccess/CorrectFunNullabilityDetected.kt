import kotlinApi.*

internal class A {
    fun foo(t: KotlinInterface): Int {
        return t.nullableFun()!!.length + t.notNullableFun().length
    }
}