@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann


fun test(a: Int) = <selection>@Ann a + a * 2</selection>