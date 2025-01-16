// "Add constructor parameters from Base(String)" "true"
open class Base private constructor(p1: Int, val p2: Int) {
    private constructor() : this(0, 1)
    protected constructor(s: String) : this(s.length, 1)
}

class C : Base<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// IGNORE_K2