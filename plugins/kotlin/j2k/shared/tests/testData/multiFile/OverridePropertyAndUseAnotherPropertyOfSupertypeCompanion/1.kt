// ERROR: 'type' hides member of supertype 'K' and needs an 'override' modifier.
internal class J : K() {
    val type: Int
        get() = FOO
}
