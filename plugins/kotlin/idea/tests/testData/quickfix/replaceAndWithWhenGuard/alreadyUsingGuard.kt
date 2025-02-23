// IGNORE_K1
// "Replace '&&' with 'if'" "false"
// WITH_STDLIB
// K2_AFTER_ERROR: The feature "when guards" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xwhen-guards', but note that no stability guarantees are provided.

fun test(a: Any) {
    when (a) {
        is Int if a % 2<caret> == 0 -> {}
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceAndWithWhenGuardFix