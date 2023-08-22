// ERROR: Unresolved reference: getX
internal class A {
    var x: Int = 0

    fun init() {
        getX + 1
    }
}
