// "Replace '&&' with 'if'" "false"
// WITH_STDLIB

fun test(a: Any) {
    when (a) {
        is Int && a % 2<caret> == 0, is String -> {}
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceAndWithWhenGuardFix