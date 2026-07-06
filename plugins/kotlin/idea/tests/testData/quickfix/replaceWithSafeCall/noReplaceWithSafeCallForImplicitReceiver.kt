// "Replace with safe (?.) call" "false"
// ACTION: Add non-null asserted (foo()!!) call
// ACTION: Replace with safe (this?.) call
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type A?
// K2_AFTER_ERROR: UNSAFE_CALL
// K2_ERROR: UNSAFE_CALL

class A {
    fun foo() {
    }
}

fun A?.bar() {
    <caret>foo()
}
