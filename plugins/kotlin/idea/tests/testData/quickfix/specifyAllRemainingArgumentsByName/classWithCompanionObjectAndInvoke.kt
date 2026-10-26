// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: NONE_APPLICABLE
class Foo(a: String, val b: String) {
    companion object
}

operator fun Any.invoke(value: String) = Unit

fun test() {
    val bar = Foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
