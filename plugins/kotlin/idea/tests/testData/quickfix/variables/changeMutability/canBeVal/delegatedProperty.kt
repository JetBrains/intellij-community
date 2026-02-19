// "Change to 'val'" "true"
import kotlin.reflect.KProperty

fun test() {
    var foo: String by <caret>Delegate()
}

class Delegate {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return ""
    }
}
// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix