// "class org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix" "false"
// ERROR: This type has a constructor, and thus must be initialized here
// K2_AFTER_ERROR: Cannot access 'constructor(p: Int): Base': it is private in '/Base'.
// K2_AFTER_ERROR: This type has a constructor, so it must be initialized here.
open class Base private constructor(p: Int)

class C : Base<caret>
