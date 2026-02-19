// IGNORE_K1
package test

data class D(val x: Int)

val aaa = D(42).co<caret>py()

// REF: (in test.D).D(Int)
