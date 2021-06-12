class K1 : MyJavaClass() {
    override fun meth(c: Collection<Int>?) = Unit
}

class K2 : MyJavaClass() {
    override fun meth(c: Collection<Int>) = Unit
}

class K3 : MyJavaClass() {
    override fun meth(c: MutableCollection<Int>?) = Unit
}

class K4 : MyJavaClass() {
    override fun meth(c: MutableCollection<Int>) = Unit
}

class K5 : MyJavaClass() {
    override fun meth(c: Collection<Int?>?) = Unit
}

class K6 : MyJavaClass() {
    override fun meth(c: Collection<Int?>) = Unit
}

class K7 : MyJavaClass() {
    override fun meth(c: MutableCollection<Int?>?) = Unit
}

class K8 : MyJavaClass() {
    override fun meth(c: MutableCollection<Int?>) = Unit
}
