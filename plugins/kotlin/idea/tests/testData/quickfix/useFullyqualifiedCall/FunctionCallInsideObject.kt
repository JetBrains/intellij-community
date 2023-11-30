// "Use fully qualified call" "true"

package one.two.three

object Test1 {
    fun foo(x: Any) {}
    fun foo(f: () -> Unit) {}
    object Scope {
        fun foo(r: Runnable) {}

        fun test(f: () -> Unit) {
            <caret>foo(f)
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseFullyQualifiedCallFix