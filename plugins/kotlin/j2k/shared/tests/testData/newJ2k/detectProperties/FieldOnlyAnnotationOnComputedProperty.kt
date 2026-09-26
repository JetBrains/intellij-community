// ERROR: This annotation is not applicable to target 'member property without backing field or delegate'. Applicable targets: field, expression
class FieldOnlyEntity(private val location: String) {
    @FieldOnly
    val summary: String?
        get() = "Summary for " + location
}
