// "Change type of 'foo' to 'Any'" "true"
class O
class P

val foo: O = when {
    true -> O()
    else -> P()<caret>
}

/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix