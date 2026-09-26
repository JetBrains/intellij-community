// "class org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix" "false"
// ACTION: Change to constructor invocation
// ERROR: This type has a constructor, and thus must be initialized here
// K2_AFTER_ERROR: SUPERTYPE_NOT_INITIALIZED
// K2_ERROR: SUPERTYPE_NOT_INITIALIZED
open class Base

class C : Base<caret>
