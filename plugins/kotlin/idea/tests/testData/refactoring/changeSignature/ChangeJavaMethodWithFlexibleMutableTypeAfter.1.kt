class K1 : MyJavaClass() {
    override fun <T> meth(c: Collection<T>?, s: String) = Unit
}

class K2 : MyJavaClass() {
    override fun <T> meth(c: Collection<T>, s: String) = Unit
}

class K3 : MyJavaClass() {
    override fun <T> meth(c: MutableCollection<T>?, s: String) = Unit
}

class K4 : MyJavaClass() {
    override fun <T> meth(c: MutableCollection<T>, s: String) = Unit
}

class K5 : MyJavaClass() {
    override fun <T> meth(c: Collection<T?>?, s: String) = Unit
}

class K6 : MyJavaClass() {
    override fun <T> meth(c: Collection<T?>, s: String) = Unit
}

class K7 : MyJavaClass() {
    override fun <T> meth(c: MutableCollection<T?>?, s: String) = Unit
}

class K8 : MyJavaClass() {
    override fun <T> meth(c: MutableCollection<T?>, s: String) = Unit
}
