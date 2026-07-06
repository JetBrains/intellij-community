// "Add constructor parameters from Base(Int, Int)" "true"
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: SUPERTYPE_NOT_INITIALIZED
open class Base(`fun`: Int, val `class`: Int)

class C : Base<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix