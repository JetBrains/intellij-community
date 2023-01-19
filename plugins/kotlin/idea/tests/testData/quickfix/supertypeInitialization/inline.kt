// "Change to constructor invocation" "false"
// DISABLE-ERRORS
// ACTION: Introduce import alias
inline class I(val x: Int)

class II : I<caret>