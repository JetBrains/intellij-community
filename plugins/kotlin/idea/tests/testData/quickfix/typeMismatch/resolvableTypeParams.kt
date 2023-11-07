// "Change parameter 'n' type of function 'foo' to 'T'" "true"
fun <T> bar(t: T) {
    fun foo(n: Int) {

    }

    foo(<caret>t)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
/* IGNORE_K2 */