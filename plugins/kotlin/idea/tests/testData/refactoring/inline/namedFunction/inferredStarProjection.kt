
fun foo(array: Array<*>) = inlineMe(array)
fun <U> in<caret>lineMe(array: Array<U>) = array.size

// IGNORE_K1