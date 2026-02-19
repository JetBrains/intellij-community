// "Add constructor parameters from Base(Int, Int)" "true"
open class Base(p1: Int, private val p2: Int = 0)

class C(p: Int) : Base<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix