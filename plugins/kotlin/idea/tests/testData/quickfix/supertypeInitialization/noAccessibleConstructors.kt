// "class org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix" "false"
// ERROR: This type has a constructor, and thus must be initialized here
// K2_AFTER_ERROR: No value passed for parameter 'p'.
// K2_AFTER_ERROR: This type has a constructor, so it must be initialized here.
open class Base private constructor(p: Int)

class C : Base<caret>
