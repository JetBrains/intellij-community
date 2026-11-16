// ERROR: WRONG_ANNOTATION_TARGET
class FieldOnlyEntity(private val location: String?) {
    @FieldOnly
    val summary: String?
        get() = "Summary for " + location
}
