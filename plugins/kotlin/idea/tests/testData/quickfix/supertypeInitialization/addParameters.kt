// "Add constructor parameters from Base(Int, Int)" "true"
// K2_ERROR: No value passed for parameter 'p1'.
// K2_ERROR: This type has a constructor, so it must be initialized here.
open class Base(p1: Int, private val p2: Int = 0)

class C(p: Int) : Base<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix