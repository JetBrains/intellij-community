// "Configure arguments for the feature: explicit backing fields" "true"
// LANGUAGE_VERSION: 2.3
// APPLY_QUICKFIX: false
// DISABLE_K2_ERRORS

interface Parent
class Child : Parent

class Point {
    val prop: Parent
        field = <caret>Child()
}