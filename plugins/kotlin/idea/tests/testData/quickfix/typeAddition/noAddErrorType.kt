// "Specify type explicitly" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// ACTION: Add getter
// ACTION: Convert property initializer to getter
// ACTION: Convert property to function
// ACTION: Convert to lazy property
// ACTION: Introduce backing property
// ACTION: Move to companion object
// ACTION: Move to constructor
// ACTION: Specify type explicitly
// ERROR: Unresolved reference: foo

class A() {
    public val <caret>t = foo()
}
/* IGNORE_FIR */
