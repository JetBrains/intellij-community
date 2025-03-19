package test

actual open class C actual constructor(b: Boolean)

fun test() {
    val c = C(false)
}