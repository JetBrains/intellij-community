// "Replace '@JvmField' with 'const'" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.2
interface IFace {
    companion object {
        <caret>@JvmField val a = "Lorem ipsum"
    }
}

// IGNORE_K2
// Support was dropped by K2, see KT-71481

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix
