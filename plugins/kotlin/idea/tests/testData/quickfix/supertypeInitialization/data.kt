// "Change to constructor invocation" "false"
// DISABLE-ERRORS
// ACTION: Introduce import alias
data class D(val x: Int)

class DD : D<caret>