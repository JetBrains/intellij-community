open class <caret>Base(val x: Int) {
    fun m() {
        println(x)
    }
}
class Child : Base(1)
