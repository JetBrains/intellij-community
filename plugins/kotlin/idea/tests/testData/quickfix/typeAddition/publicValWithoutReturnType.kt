// "Specify type explicitly" "true"
package a

public fun <T> emptyList(): List<T> = null!!

public val <caret>l = emptyList<Int>()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.SpecifyTypeExplicitlyIntention