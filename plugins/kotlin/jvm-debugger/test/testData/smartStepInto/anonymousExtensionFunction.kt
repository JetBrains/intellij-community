fun foo() {
    val x = Expandable()
    x.myself().extendedFun()<caret>
}

class Expandable {
    fun myself() = this
}
fun Expandable.extendedFun() { println() }

// EXISTS: myself(), extendedFun()
// IGNORE_K2