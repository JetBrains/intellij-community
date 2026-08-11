// "Change to 'val'" "false"
// ACTION: Add initializer
// ACTION: Convert member to extension
// ACTION: Initialize with constructor parameter
// ACTION: Introduce backing property
// ACTION: Move to companion object
// ERROR: Property must be initialized
// K2_AFTER_ERROR: MUST_BE_INITIALIZED
// K2_ERROR: MUST_BE_INITIALIZED
class Test {
    var foo<caret>
        get() {
            return 1
        }
}
