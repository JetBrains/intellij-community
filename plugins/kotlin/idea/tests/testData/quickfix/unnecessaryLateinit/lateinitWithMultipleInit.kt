// "Remove 'lateinit' modifier" "true"

class Foo {
    <caret>lateinit var bar: String
    var baz: Int

    init {
        baz = 1
    }

    init {
        bar = ""
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase