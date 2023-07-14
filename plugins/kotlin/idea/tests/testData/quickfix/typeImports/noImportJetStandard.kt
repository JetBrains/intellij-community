// "Remove initializer from property" "true"
package a

class M {
    interface A {
        abstract val i = <caret>10
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix