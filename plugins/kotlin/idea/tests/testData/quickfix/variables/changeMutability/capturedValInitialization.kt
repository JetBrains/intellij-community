// "Change to 'var'" "true"
fun exec(f: () -> Unit) = f()

fun foo() {
    val x: Int
    exec {
        <caret>x = 42
    }
}

/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix