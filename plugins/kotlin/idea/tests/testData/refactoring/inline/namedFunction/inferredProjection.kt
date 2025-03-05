
fun foo(array: Array<in String>) = inlineMe(array)
fun <U> in<caret>lineMe(array: Array<U>) = array.size

// IGNORE_K1