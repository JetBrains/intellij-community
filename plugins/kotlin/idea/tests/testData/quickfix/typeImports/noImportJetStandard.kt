// "Remove initializer from property" "true"
// K2_ERROR: ABSTRACT_PROPERTY_WITH_INITIALIZER
package a

class M {
    interface A {
        abstract val i = <caret>10
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemovePartsFromPropertyFixFactory$RemovePartsFromPropertyFix