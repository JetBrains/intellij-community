typealias <caret>A<T> = B<T>

class B<T>

val map = mapOf<A<*>, A<String>>()
val value: A<*>? = null
val nested: List<A<*>> = emptyList()
val doublyNested: A<B<*>>? = null
