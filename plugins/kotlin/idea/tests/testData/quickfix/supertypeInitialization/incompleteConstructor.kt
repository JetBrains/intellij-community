// "Add constructor parameters from Base(Int, Int)" "true"
open class Base(p1: Int, val p2: Int)

class C private constructor : Base<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// IGNORE_K2