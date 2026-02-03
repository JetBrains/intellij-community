// "Change to constructor invocation" "false"
// DISABLE_ERRORS
// ACTION: Introduce import alias
inline class I(val x: Int)

class II : I<caret>
