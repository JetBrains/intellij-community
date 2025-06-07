// "class org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix" "false"
// ACTION: Change to constructor invocation
// ERROR: Unresolved reference: XXX
// ERROR: This type has a constructor, and thus must be initialized here
// K2_AFTER_ERROR: No value passed for parameter 'p1'.
// K2_AFTER_ERROR: This type has a constructor, so it must be initialized here.
// K2_AFTER_ERROR: Unresolved reference 'XXX'.
open class Base(p1: XXX)

class C : Base<caret>
