// "Add constructor parameters from Base(Int)" "true"
// ACTION: Add constructor parameters from Base(Char, Char, String, Int, Any?,...)
// ACTION: Add constructor parameters from Base(Char, Char, String?, Int, Any?)
// ACTION: Add constructor parameters from Base(Int)
// ACTION: Change to constructor invocation
// ACTION: Introduce import alias
// K2_ERROR: None of the following candidates is applicable:<br><br>constructor(p: Int): Base:<br>  No value passed for parameter 'p'.<br><br>constructor(p1: Char, p2: Char, p3: String, p4: Int, p5: Any?, p6: String): Base:<br>  No value passed for parameter 'p1'.<br>  No value passed for parameter 'p2'.<br>  No value passed for parameter 'p3'.<br>  No value passed for parameter 'p4'.<br>  No value passed for parameter 'p5'.<br>  No value passed for parameter 'p6'.<br><br>constructor(p1: Char, p2: Char, p3: String?, p4: Int, p5: Any?): Base:<br>  No value passed for parameter 'p1'.<br>  No value passed for parameter 'p2'.<br>  No value passed for parameter 'p3'.<br>  No value passed for parameter 'p4'.<br>  No value passed for parameter 'p5'.
// K2_ERROR: This type has a constructor, so it must be initialized here.

open class Base {
    constructor(p: Int){}
    constructor(p1: Char, p2: Char, p3: String, p4: Int, p5: Any?, p6: String){}
    constructor(p1: Char, p2: Char, p3: String?, p4: Int, p5: Any?){}
}

class C : Base<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix