typealias Names = List<String>

open class Base<T> {
    open fun mutable(items: MutableList<String>) {}

    open fun aliased(names: Names) {}

    open fun nested(map: Map<String, List<Int?>>) {}

    open fun generic(items: Collection<T>) {}
}
