class K1 : MyJavaClass() {
    override fun coll(c: Collection<Any?>?) = Unit
}

class K2 : MyJavaClass() {
    override fun coll(c: Collection<Any?>) = Unit
}

class K3 : MyJavaClass() {
    override fun coll(c: MutableCollection<Any?>) = Unit
}

class K4 : MyJavaClass() {
    override fun coll(c: MutableCollection<Any?>?) = Unit
}
