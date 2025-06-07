// "Add constructor parameters from Base(Int, IntArray)" "true"
open class Base(p1: Int, vararg p2: Int)

class C : Base<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
