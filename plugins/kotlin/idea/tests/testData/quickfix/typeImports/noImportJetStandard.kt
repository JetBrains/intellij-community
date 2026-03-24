// "Remove initializer from property" "true"
// K2_ERROR: Property with initializer cannot be abstract.
package a

class M {
    interface A {
        abstract val i = <caret>10
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemovePartsFromPropertyFixFactory$RemovePartsFromPropertyFix