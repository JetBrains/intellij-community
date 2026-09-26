// INPLACE_VARIABLE_NAME: klass
class A

fun test() {
    <caret>A::class.java.getDeclaredField("name")
}