// INPLACE_VARIABLE_NAME: klass
class A {
    companion object
}

fun test() {
    <caret>A::class.java.getDeclaredField("name")
}