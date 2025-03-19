// NEW_NAME: value
// RENAME: member
// SHOULD_FAIL_WITH: Variable 'value' is already declared in function 'value2'
class A {
    fun value2(val<caret>ue1: Boolean?): Suggestion {
        val value: String? = null
        return if (value1 == null) Suggestion.NO else value2(value1)
    }
    fun value2(value: Boolean) = if (value) Suggestion.YES else Suggestion.NO
}