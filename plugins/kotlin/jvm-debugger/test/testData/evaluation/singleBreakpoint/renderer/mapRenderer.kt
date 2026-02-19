package mapRenderer

private class MyMap : AbstractMap<String, String>() {
    private val implementationDetail = mapOf("a" to "aaaa", "b" to "bbbb")

    override val entries: Set<Map.Entry<String, String>>
        get() = implementationDetail.entries
}

fun main() {
    val emptyMap = emptyMap<String, String>()
    val nonEmptyMap = mapOf<String, String>(
        "k1" to "v1",
        "k2" to "v2"
    )

    val builtEmptyMap = buildMap<String, String> {}
    val builtNonEmptyMap = buildMap<String, String> {
        put("bk1", "bv1")
        put("bk2", "bv2")
    }

    val myMap = MyMap()

    //Breakpoint!
    run {}
}

// PRINT_FRAME
