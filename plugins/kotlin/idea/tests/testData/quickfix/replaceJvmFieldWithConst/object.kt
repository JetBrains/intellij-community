// "Replace '@JvmField' with 'const'" "true"
// WITH_STDLIB
object Foo {
    <caret>@JvmField private val a = "Lorem ipsum"
}
