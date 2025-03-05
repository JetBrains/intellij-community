// "Add constructor parameters from Base(Int, Int, Any, String, String,...)" "false"
// K2_AFTER_ERROR: No value passed for parameter 'p1'.
// K2_AFTER_ERROR: No value passed for parameter 'p2'.
// K2_AFTER_ERROR: No value passed for parameter 'p3'.
// K2_AFTER_ERROR: No value passed for parameter 'p4'.
// K2_AFTER_ERROR: No value passed for parameter 'p5'.
// K2_AFTER_ERROR: No value passed for parameter 'p6'.
// K2_AFTER_ERROR: This type has a constructor, so it must be initialized here.
open class Base<T>(p1: Int, private val p2: Int, p3: Any, p4: String, p5: T, p6: Int)

class C(p: Int, p2: Int, p3: String, p4: Any, p5: String, val p6: Int) : Base<String><caret>
