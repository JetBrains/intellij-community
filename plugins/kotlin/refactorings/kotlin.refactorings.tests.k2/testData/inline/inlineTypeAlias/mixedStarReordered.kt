typealias <caret>Reordered<X, Y> = Pair<Y, X>

val first: Reordered<*, String>? = null
val second: Reordered<Int, *>? = null
