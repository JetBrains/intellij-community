// ERROR: Argument type mismatch: actual type is 'Topic<kotlin.String?>', but 'Topic<kotlin.String>' was expected.
// ERROR: Argument type mismatch: actual type is 'Topic<kotlin.String?>', but 'Topic<kotlin.String>' was expected.
internal class J {
    var topic: Topic<String?> = Topic<String?>()

    fun test(k: K) {
        k.f<String>(topic)
        k.f<String>(topic)
    }
}

internal class Topic<T>
