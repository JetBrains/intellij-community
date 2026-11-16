// ERROR: NULL_FOR_NONNULL_TYPE
// ERROR: UPPER_BOUND_VIOLATED
// ERROR: UPPER_BOUND_VIOLATED
class Foo {
    fun test(s: String) {
        J.unannotated<String>(s)
        J.notNullTypeParameter<String>(s)
        J.nullableTypeParameter<String>(s)
        J.notNullBound<String>(s)

        // the type argument is written explicitly: a different branch of getExplicitTypeArguments
        J.notNullTypeParameter<String?>(s)

        // a null argument must keep the type argument nullable, or the result would not compile
        J.notNullTypeParameter<Any?>(null)

        // conservative: the null in the V position also relaxes K
        J.twoTypeParameters<String, Any?>(s, null)
    }
}
