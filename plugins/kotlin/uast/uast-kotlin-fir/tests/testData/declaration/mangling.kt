class Foo {
    internal var internalVarPrivateSet: String
        private set

    protected lateinit var protectedLateinitVar: String

    public var int1: Int
        private set
        protected get
    public var int2: Int
        public get
        internal set

    open fun bar1(a: Int, b:Any, c:Foo): Unit {}
    internal fun bar2(a: Sequence, b: Unresolved) {}
    private fun bar3(x: Foo.Inner, vararg y: Inner) = "str"

    class Inner {}
}