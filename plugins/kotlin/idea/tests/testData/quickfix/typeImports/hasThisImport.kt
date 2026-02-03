// "Remove initializer from property" "true"
package a

public fun <T> emptyList(): List<T> = null!!

class M {
    interface A {
        val l = <caret>emptyList<Int>()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemovePartsFromPropertyFixFactory$RemovePartsFromPropertyFix