// ERROR: ARGUMENT_TYPE_MISMATCH
// ERROR: ARGUMENT_TYPE_MISMATCH
internal class J {
    var topic: Topic<String?> = Topic<String?>()

    fun test(k: K) {
        k.f<String>(topic)
        k.f<String>(topic)
    }
}

internal class Topic<T>
