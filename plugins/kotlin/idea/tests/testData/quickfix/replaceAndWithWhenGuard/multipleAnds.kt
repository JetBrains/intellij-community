// IGNORE_K1
// "Replace '&&' with 'if'" "true"
// WITH_STDLIB
// K2_AFTER_ERROR: The feature "when guards" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xwhen-guards', but note that no stability guarantees are provided.

fun test(a: Any) {
    when (a) {
        is Int && a % 2<caret> == 0 && a > 0 && a != 2 -> {}
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceAndWithWhenGuardFix