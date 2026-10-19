typealias <caret>A<T> = B<T>

class B<T>
class C
@Target(AnnotationTarget.TYPE)
annotation class Mark

val out = mapOf<A<out C>, Unit>()
val `in` = mapOf<A<in C>, Unit>()
val nested: List<A<out C>> = emptyList()
val annotated: @Mark A<out C> = TODO()
