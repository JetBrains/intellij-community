// "Add constructor parameters from A(vararg Int)" "true"

// K2_ERROR: This type has a constructor, so it must be initialized here.
open class A(vararg i: Int)

class B(i: Int) : A<caret>
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix