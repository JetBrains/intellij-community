abstract class Base<T>(t: T)

abstract class Base<caret>Base(
    t: String,
    protected val bool: Boolean,
) : Base<String>(t) {
    init {
        init()
    }

    fun canInlineThisOnly() = bool
}

class Foo(
    t: String,
    bool: Boolean,
) : BaseBase(t, bool) {
    init {
        init()
    }
}