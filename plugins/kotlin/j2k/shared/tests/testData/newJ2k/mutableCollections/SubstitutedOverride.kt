internal open class Base<T> {
    open fun foo(items: List<T?>?) {
    }
}

internal class Sub : Base<MutableMap<String?, Int?>?>() {
    override fun foo(items: List<MutableMap<String?, Int?>?>?) {
    }
}

internal class KotlinSub : KotlinBase<MutableMap<String?, Int?>?>() {
    public override fun bar(items: List<out MutableMap<String?, Int?>?>) {
    }
}
