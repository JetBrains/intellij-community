package test

actual open class C(b: Boolean)

fun test() {
    val c = C(false)
}