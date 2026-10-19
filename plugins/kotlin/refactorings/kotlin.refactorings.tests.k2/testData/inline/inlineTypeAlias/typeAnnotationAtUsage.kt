@Target(AnnotationTarget.TYPE)
annotation class Foo

typealias Void = @Foo Unit

val a: <caret>Void = Unit
