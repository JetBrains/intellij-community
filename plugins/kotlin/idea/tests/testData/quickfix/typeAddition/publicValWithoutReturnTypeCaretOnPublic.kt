// "Specify type explicitly" "true"
package a

public fun <T> emptyList(): List<T> = null!!

<caret>public val l = emptyList<Int>()
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention