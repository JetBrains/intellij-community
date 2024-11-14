// "Specify type explicitly" "true"
// IGNORE_K1
package test

class MyClass

class Cell<T>(t: T)

class WithGenerics<T>(t: T)

typealias TypeAlias<TT> = WithGenerics<Cell<TT>>

// in K1 Mode, explicit type would be fully expanded
val <caret>list = TypeAlias(Cell(MyClass()))

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.SpecifyTypeExplicitlyIntention