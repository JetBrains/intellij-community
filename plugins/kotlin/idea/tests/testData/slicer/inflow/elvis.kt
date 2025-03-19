// FLOW: IN
fun testNullCheckConditionalAssignment() {
    val value: String? = null
    val result = value?.length ?: 0
    re<caret>sult
}