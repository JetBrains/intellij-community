// "Change to property access" "true"
class A(val ff: String)

fun x() {
    val y = A("").f<caret>f()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToPropertyAccessFix