// "Add constructor parameters from A(vararg String)" "true"
open class A(vararg strings: String = arrayOf("a", "b"))

class B : A<caret>
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix