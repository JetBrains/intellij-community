// "Replace '&&' with 'if'" "false"
// WITH_STDLIB
// K2_ERROR: SYNTAX
// K2_AFTER_ERROR: SYNTAX

fun test(a: Any) {
    when (a) {
        is Int, is String && a.isBlank()<caret> -> {}
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceAndWithWhenGuardFix