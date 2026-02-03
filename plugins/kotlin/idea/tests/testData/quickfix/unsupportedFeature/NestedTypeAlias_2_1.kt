// "Configure arguments for the feature: nested type aliases" "false"
// LANGUAGE_VERSION: 2.1
// APPLY_QUICKFIX: false
// DISABLE_K2_ERRORS

class A

class C {
    <caret>typealias TA = A

    fun test(): TA = TA()
}
