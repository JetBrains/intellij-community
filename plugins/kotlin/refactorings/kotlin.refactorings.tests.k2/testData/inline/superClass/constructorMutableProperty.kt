open class <caret>Base(var value: Int) {
    fun increment() {
        value++
    }
}

class Child : Base(1)

fun use(child: Child) {
    child.value = 2
    child.increment()
}
