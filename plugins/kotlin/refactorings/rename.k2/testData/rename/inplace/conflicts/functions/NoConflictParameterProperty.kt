// NEW_NAME: value
// RENAME: member
class A {
    val value: String? = null

    fun value2(val<caret>ue1: Boolean?): Suggestion {
        return if (value1 == null) Suggestion.NO else value2(value1)
    }
    fun value2(value: Boolean) = if (value) Suggestion.YES else Suggestion.NO
}