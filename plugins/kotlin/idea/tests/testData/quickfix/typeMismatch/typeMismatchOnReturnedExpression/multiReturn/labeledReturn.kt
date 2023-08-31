// "Change return type of enclosing function 'foo' to 'Any'" "true"
// WITH_STDLIB
fun foo(n: Int): Boolean {
    if (true) return "foo"<caret>
    n.let {
        return@foo 1
    }
}

/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing