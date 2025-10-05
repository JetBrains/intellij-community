// "Replace 'runBlocking' with inline code" "true"
// WITH_COROUTINES
package test

import kotlinx.coroutines.runBlocking

fun customFunction(block: suspend () -> Unit) {
    // implementation not relevant
}

fun main() {
    customFunction {
        run {
            <caret>runBlocking {
                println("Hello")
            }
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.RunBlockingInSuspendFunctionInspection$createQuickFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.RunBlockingInSuspendFunctionInspection$createQuickFix$1