// "Remove 'lateinit' modifier" "true"
// ERROR: There's a cycle in the delegation calls chain
// K2_AFTER_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL

class Foo {
    <caret>lateinit var bar: String

    constructor() {
        bar = ""
    }

    constructor(a: Int) : this(a) {
        bar = "a"
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase