// "Add constructor parameters from Base(Int, vararg Int)" "true"
open class Base(p1: Int, vararg p2: Int)

class C(vararg p2: Int) : Base<caret>
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix