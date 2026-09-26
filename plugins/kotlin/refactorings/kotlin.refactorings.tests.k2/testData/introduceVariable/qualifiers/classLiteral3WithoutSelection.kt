class A {
    companion object
}

fun test() {
    <caret>A.Companion::class.java.getDeclaredField("name")
}