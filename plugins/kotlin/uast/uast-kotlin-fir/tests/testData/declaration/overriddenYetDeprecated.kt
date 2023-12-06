package test.pkg

@Suppress("DeprecatedCallableAddReplaceWith","EqualsOrHashCode")
@Deprecated("Use Jetpack preference library", level = DeprecationLevel.ERROR)
class Foo {
    fun foo()

    @Deprecated("Blah blah blah 1", level = DeprecationLevel.ERROR)
    override fun toString(): String = "Hello World"

    /**
     * My description
     * @deprecated Existing deprecation message.
     */
    @Deprecated("Blah blah blah 2", level = DeprecationLevel.ERROR)
    override fun hashCode(): Int = 0
}