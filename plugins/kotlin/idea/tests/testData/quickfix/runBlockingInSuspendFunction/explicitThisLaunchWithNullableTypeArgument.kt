// "Fix all ''runBlocking' inside suspend function' problems in file" "true"
// WITH_COROUTINES

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

suspend fun main() {
    var v = run<caret>Blocking<String?> {
        this.launch {
            println("Hello, World!")
        }
        "foo"
    }
    v = null;
}

// FUS_QUICKFIX_NAME: com.intellij.codeInspection.actions.CleanupInspectionIntention
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.actions.CleanupInspectionIntention