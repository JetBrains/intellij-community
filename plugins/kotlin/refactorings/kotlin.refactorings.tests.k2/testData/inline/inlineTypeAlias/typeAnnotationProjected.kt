@Target(AnnotationTarget.TYPE)
annotation class Foo

@Target(AnnotationTarget.TYPE)
annotation class Bar

class Box<T>
typealias <caret>Annotated<T> = @Foo Box<T>

val projected: @Bar Annotated<out String> = TODO()
