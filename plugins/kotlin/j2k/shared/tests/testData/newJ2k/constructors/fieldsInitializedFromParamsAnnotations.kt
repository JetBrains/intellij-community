// ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET
// ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET
// ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET
internal class C(
    @field:Deprecated("") private val p1: Int,
    @param:Deprecated("") private val myP2: Int,
    @param:Deprecated("") var p3: Int,
    @FieldOnly private val p4: Int,
    @FieldAndMethod private val p5: Int,
    @field:FieldAndParam private val p6: Int
)
