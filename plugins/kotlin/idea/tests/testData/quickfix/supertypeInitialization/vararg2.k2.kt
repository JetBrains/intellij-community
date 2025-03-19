// "Add constructor parameters from Base(Int, vararg Int)" "true"
open class Base(p1: Int, vararg p2: Int)

class C(p2: IntArray) : Base<caret>
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix