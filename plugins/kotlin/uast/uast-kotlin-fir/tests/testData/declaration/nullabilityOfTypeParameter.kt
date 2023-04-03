class NonNullUpperBound<T : Any>(ctorParam: T) {
    fun inheritedNullability(i: T): T = i
    fun explicitNullable(e: T?): T? = e
}

class NullableUpperBound<T : Any?>(ctorParam: T) {
    fun inheritedNullability(i: T): T = i
    fun explicitNullable(e: T?): T? = e
}

class UnspecifiedUpperBound<T>(ctorParam: T) {
    fun inheritedNullability(i: T): T = i
    fun explicitNullable(e: T?): T? = e
}

fun <T : Any> topLevelNonNullUpperBoundInherited(t: T) = t
fun <T : Any> topLevelNonNullUpperBoundExplicitNullable(t: T?) = t

fun <T : Any?> topLevelNullableUpperBoundInherited(t: T) = t
fun <T : Any?> topLevelNullableUpperBoundExplicitNullable(t: T?) = t

fun <T> topLevelUnspecifiedUpperBoundInherited(t: T) = t
fun <T> topLevelUnspecifiedUpperBoundExplicitNullable(t: T?) = t
