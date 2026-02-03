// "Replace 'runBlocking' with inline code" "true"
// WITH_COROUTINES

import kotlinx.coroutines.runBlocking

suspend fun something() {
    run<caret>Blocking {
        code()
    }
}

suspend fun code() {
    TODO()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.RunBlockingInSuspendFunctionInspection$createQuickFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.RunBlockingInSuspendFunctionInspection$createQuickFix$1