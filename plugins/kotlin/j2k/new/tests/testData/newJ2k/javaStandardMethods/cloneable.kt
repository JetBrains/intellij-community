internal class C1 : Cloneable {
    @Throws(CloneNotSupportedException::class)
    public override fun clone(): Any {
        return super.clone()
    }
}

internal open class C2 : Cloneable {
    @Throws(CloneNotSupportedException::class)
    public override fun clone(): Any {
        return super.clone()
    }
}

internal class C3 : C2() {
    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        return super.clone()
    }
}

internal class C4 : C2() {
    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        return super.clone()
    }
}
