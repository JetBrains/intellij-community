// "Add constructor parameters from Base(Int, vararg Int)" "true"
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: SUPERTYPE_NOT_INITIALIZED
open class Base(p1: Int, vararg p2: Int)

class C(vararg p2: Int) : Base<caret>
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix