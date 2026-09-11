// "Specify type explicitly" "true"
package a

public fun <T> emptyList(): List<T> = null!!

<caret>public val l = emptyList<Int>()

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.SpecifyTypeExplicitlyIntention