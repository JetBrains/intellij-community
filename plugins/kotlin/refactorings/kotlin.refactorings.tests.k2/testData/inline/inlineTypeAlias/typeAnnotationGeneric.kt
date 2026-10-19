@Target(AnnotationTarget.TYPE)
annotation class Foo

@Target(AnnotationTarget.TYPE)
annotation class Bar

class Box<T>
typealias <caret>Annotated<T> = @Foo Box<T>

val generic: Annotated<String> = TODO()
val combined: @Bar Annotated<String> = TODO()
val nullable: Annotated<String>? = null
