// ERROR: Argument type mismatch: actual type is 'Topic<String?>', but 'Topic<String>' was expected.
// ERROR: Argument type mismatch: actual type is 'Topic<String?>', but 'Topic<String>' was expected.
internal class J {
    var topic: Topic<String?> = Topic<String?>()

    fun test(k: K) {
        k.f<String>(topic)
        k.f<String>(topic)
    }
}

internal class Topic<T>
