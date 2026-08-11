// "class org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix" "false"
// ACTION: Change type of 'b' to 'A'
// ACTION: Convert property initializer to getter
// ACTION: Let 'A' implement interface 'B'
// ERROR: Type mismatch: inferred type is A but B was expected
// K2_AFTER_ERROR: INITIALIZER_TYPE_MISMATCH
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

class A constructor() {}
interface B

val b: B = <caret>A()