// "Surround with lambda" "true"
fun subclass() {
    base(<caret>Leaf())
}

fun base(base: () -> Base) {}

open class Base {}
class Leaf : Base()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaFix
/* IGNORE_K2 */