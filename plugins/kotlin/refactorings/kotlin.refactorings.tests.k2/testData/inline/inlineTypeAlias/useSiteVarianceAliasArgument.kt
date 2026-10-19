typealias <caret>A<T> = B<T>

class B<T>
typealias Inner = String

val plain: A<Inner> = TODO()
val projected: A<out Inner> = TODO()
// IGNORE_K2