@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

fun Int.plusL<caret>ogged(other: Int): Int {
    println("side effect")
    return this + other
}

fun test(a: Int) = @Ann a.plusLogged(1)