// "Replace '&&' with 'if'" "false"
// WITH_STDLIB

fun test(a: Any) {
    when (a) {
        is Int && a % 2 == 0 -> {}
        else<caret> -> {}
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceAndWithWhenGuardFix