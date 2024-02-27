internal interface Cloneable

internal open class C1 : Cloneable, kotlin.Cloneable {
    @Throws(CloneNotSupportedException::class)
    public override fun clone(): Any {
        return super.clone()
    }
}

internal class C2 : C1(), Cloneable {
    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        return super.clone()
    }
}
