// ERROR: Type argument is not within its bounds: type parameter 'T (of fun <T : Any> notNullTypeParameter)' must be subtype of 'Any', but actual: 'String?'.
// ERROR: Inapplicable candidate(s): static fun <T : Any> notNullTypeParameter(value: T): T
// ERROR: Type argument is not within its bounds: type parameter 'T (of fun <T : Any> notNullTypeParameter)' must be subtype of 'Any', but actual: 'Any?'.
// ERROR: Null cannot be a value of a non-null type 'Any?'.
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
