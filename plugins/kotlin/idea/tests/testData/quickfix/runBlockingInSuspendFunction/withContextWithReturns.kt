// "Replace 'runBlocking' with 'withContext'" "true"
// WITH_COROUTINES

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

suspend fun something() {
    run<caret>Blocking(Dispatchers.Default) {
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

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RunBlockingInSuspendFunctionInspection$createQuickFix$1