// "Specify type explicitly" "false"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// ERROR: This property must either have a type annotation, be initialized or be delegated
// ACTION: Convert member to extension
// ACTION: Convert property to function
// ACTION: Move to companion object
// K2_AFTER_ERROR: This property must have an explicit type, be initialized, or be delegated.

class My {
    val <caret>x
        get() {
            return 3.14
        }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention