// "Add constructor parameters from Base(Int, vararg Int)" "true"
// K2_ERROR: No value passed for parameter 'p1'.
// K2_ERROR: This type has a constructor, so it must be initialized here.
open class Base(p1: Int, vararg p2: Int)

class C(p2: IntArray) : Base<caret>
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix