// "Replace with safe (?.) call" "true"
// WITH_STDLIB
class T(s: String?) {
    var i: Int = s<caret>.length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix