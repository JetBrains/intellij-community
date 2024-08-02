// "Replace '@JvmField' with 'const'" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.2
interface IFace {
    companion object {
        <caret>@JvmField val a = "Lorem ipsum"
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix