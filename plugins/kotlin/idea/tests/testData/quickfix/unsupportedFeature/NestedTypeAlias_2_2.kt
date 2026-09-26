// "Configure arguments for the feature: nested type aliases" "true"
// LANGUAGE_VERSION: 2.2
// APPLY_QUICKFIX: false
// DISABLE_K2_ERRORS

class A

class C {
    <caret>typealias TA = A

    fun test(): TA = TA()
}
