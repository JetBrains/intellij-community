// "class org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix" "false"
// ERROR: This type has a constructor, and thus must be initialized here
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_AFTER_ERROR: SUPERTYPE_NOT_INITIALIZED
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: SUPERTYPE_NOT_INITIALIZED
open class Base private constructor(p: Int)

class C : Base<caret>
