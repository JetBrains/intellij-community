// "Remove initializer from property" "true"
package a

class M {
    interface A {
        abstract val e = <caret>Thread()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix