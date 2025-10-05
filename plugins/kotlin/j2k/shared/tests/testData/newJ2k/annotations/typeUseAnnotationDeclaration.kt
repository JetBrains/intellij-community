@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
internal annotation class Ann

@Ann
internal class C<@Ann T> {
    fun foo(s: @Ann String?) {}
}

@Ann
internal annotation class Ann2
