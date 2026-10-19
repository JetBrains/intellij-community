typealias <caret>A<X, Y> = B<Y, X>

class B<X, Y>
class C
class D

val value: A<out C, in D>? = null
