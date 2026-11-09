internal class Derived : Base<String?>() {
    public override fun mutable(items: MutableList<String>) {
    }

    public override fun aliased(names: List<String>) {
    }

    public override fun nested(map: Map<String, out List<Int?>>) {
    }

    public override fun generic(items: Collection<out String?>) {
    }
}
