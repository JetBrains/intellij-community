
fun foo(array: Array<out String>) = inlineMe(array)
fun <U> in<caret>lineMe(array: Array<U>) = array.size

