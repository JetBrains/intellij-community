// "Remove initializer from property" "true"
package a

public fun <T> emptyList(): List<T> = null!!

class M {
    interface A {
        val l = <caret>emptyList<Int>()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix