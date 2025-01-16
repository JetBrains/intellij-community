// "Replace with safe (?.) call" "true"
// WITH_STDLIB
fun test(map: MutableMap<Int, Int?>) {
    map[3] +=<caret> 5
}

// IGNORE_K2


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix