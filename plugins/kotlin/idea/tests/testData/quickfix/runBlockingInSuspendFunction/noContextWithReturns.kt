// "Replace 'runBlocking' with 'run'" "true"
// WITH_COROUTINES

import kotlinx.coroutines.runBlocking

suspend fun something() {
    run<caret>Blocking {
        if (someCondition()) {
            return@runBlocking handleConditionOne()
        }

        if (anotherCondition()) {
            return@runBlocking handleConditionTwo()
        }

        return@runBlocking handleDefaultCase()
    }
}

suspend fun handleConditionOne() {
    TODO()
}

suspend fun handleConditionTwo() {
    TODO()
}

suspend fun handleDefaultCase() {
    TODO()
}

fun someCondition(): Boolean {
    TODO()
}

fun anotherCondition(): Boolean {
    TODO()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.RunBlockingInSuspendFunctionInspection$createQuickFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.RunBlockingInSuspendFunctionInspection$createQuickFix$1