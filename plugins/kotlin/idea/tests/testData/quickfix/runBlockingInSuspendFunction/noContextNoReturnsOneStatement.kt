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

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RunBlockingInSuspendFunctionInspection$createQuickFix$1