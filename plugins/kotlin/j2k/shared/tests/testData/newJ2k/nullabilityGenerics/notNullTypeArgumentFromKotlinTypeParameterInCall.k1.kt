internal class J {
    var topic: Topic<String> = Topic()

    fun test(k: K) {
        k.f(topic)
        k.f(topic)
    }
}

internal class Topic<T>
