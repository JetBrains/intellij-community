// "Remove 'lateinit' modifier" "true"

class Foo {
    <caret>lateinit var bar: String

    init {
        bar = ""
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase