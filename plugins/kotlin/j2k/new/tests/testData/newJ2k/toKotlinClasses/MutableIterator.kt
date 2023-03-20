class TestMutableIterator : MutableIterator<String?> {
    override fun hasNext(): Boolean {
        return false
    }

    override fun next(): String? {
        return null
    }

    override fun remove() {}
}
