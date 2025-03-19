// "class org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix" "false"
// ACTION: Change type of 'b' to 'A'
// ACTION: Convert property initializer to getter
// ACTION: Let 'A' implement interface 'B'
// ERROR: Type mismatch: inferred type is A but B was expected
// K2_AFTER_ERROR: Initializer type mismatch: expected 'B', actual 'A'.
// K2_AFTER_ERROR: Type mismatch: inferred type is 'A', but 'B' was expected.

class A constructor() {}
interface B

val b: B = <caret>A()