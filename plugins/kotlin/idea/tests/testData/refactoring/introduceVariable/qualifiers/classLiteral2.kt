class A {
    companion object
}

fun test() {
    <selection>A</selection>::class.java.getDeclaredField("name")
}