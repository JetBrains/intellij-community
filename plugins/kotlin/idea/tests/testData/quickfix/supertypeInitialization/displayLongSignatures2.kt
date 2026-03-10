// "Add constructor parameters from Base(Int)" "true"
// ACTION: Add constructor parameters from Base(Char, Char, String, Int, Any?, String)
// ACTION: Add constructor parameters from Base(Char, Char, String, Int, Any?, String, Boolean,...)
// ACTION: Add constructor parameters from Base(Int)
// ACTION: Change to constructor invocation
// ACTION: Introduce import alias
// K2_ERROR: None of the following candidates is applicable:<br><br>constructor(p: Int): Base:<br>  No value passed for parameter 'p'.<br><br>constructor(p1: Char, p2: Char, p3: String, p4: Int, p5: Any?, p6: String, b: Boolean, c: Char): Base:<br>  No value passed for parameter 'p1'.<br>  No value passed for parameter 'p2'.<br>  No value passed for parameter 'p3'.<br>  No value passed for parameter 'p4'.<br>  No value passed for parameter 'p5'.<br>  No value passed for parameter 'p6'.<br>  No value passed for parameter 'b'.<br>  No value passed for parameter 'c'.<br><br>constructor(p1: Char, p2: Char, p3: String, p4: Int, _p5: Any?, p6: String): Base:<br>  No value passed for parameter 'p1'.<br>  No value passed for parameter 'p2'.<br>  No value passed for parameter 'p3'.<br>  No value passed for parameter 'p4'.<br>  No value passed for parameter '_p5'.<br>  No value passed for parameter 'p6'.
// K2_ERROR: This type has a constructor, so it must be initialized here.

open class Base {
    constructor(p: Int){}
    constructor(p1: Char, p2: Char, p3: String, p4: Int, p5: Any?, p6: String, b: Boolean, c: Char){}
    constructor(p1: Char, p2: Char, p3: String, p4: Int, _p5: Any?, p6: String){}
}

class C : Base<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix