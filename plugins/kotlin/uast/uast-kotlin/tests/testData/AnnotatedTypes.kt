@Target(AnnotationTarget.TYPE)
annotation class MyAnnotation(val a: Int, val b: String, val c: AnnotationTarget)

fun foo(list: List<@MyAnnotation(1, "str", AnnotationTarget.TYPE) String>) {
    val a = list[2]
    val b: @MyAnnotation(2, "boo", AnnotationTarget.FILE) String = "abc"
    val c = b
    val v: @UndefinedAnnotation(unresolvedVar) String = "abc"
}