// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: @AllArgsConstructor takes every field, not only the required ones
package test

class Everything(private val required: String, private val mutable: String?, private val nonNull: String) {
    private val initialized = "skipped"

    companion object {
        private val shared: String? = null
    }
}

internal object NoInstanceFields {
    private const val CONSTANT = "c"
}
