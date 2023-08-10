class K1 : MyJavaClass() {
    override fun coll(c: Collection<Any?>?, i: Int) = Unit
}

class K2 : MyJavaClass() {
    override fun coll(c: Collection<Any?>, i: Int) = Unit
}

class K3 : MyJavaClass() {
    override fun coll(c: MutableCollection<Any?>, i: Int) = Unit
}

class K4 : MyJavaClass() {
    override fun coll(c: MutableCollection<Any?>?, i: Int) = Unit
}
