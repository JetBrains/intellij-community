class A {
    companion object
}

fun test() {
    <selection>A.Companion</selection>::class.java.getDeclaredField("name")
}