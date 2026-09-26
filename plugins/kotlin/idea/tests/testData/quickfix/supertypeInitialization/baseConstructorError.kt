// "class org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix" "false"
// ACTION: Change to constructor invocation
// ERROR: Unresolved reference: XXX
// ERROR: This type has a constructor, and thus must be initialized here
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_AFTER_ERROR: SUPERTYPE_NOT_INITIALIZED
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: SUPERTYPE_NOT_INITIALIZED
// K2_ERROR: UNRESOLVED_REFERENCE
open class Base(p1: XXX)

class C : Base<caret>
