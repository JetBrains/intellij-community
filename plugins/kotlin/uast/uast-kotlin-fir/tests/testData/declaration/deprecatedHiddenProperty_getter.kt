package test.pkg

class Test {
    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
    var pOld_getter_deprecatedOnProperty: String? = null
        get() = field ?: "null?"

    @get:Deprecated(level = DeprecationLevel.HIDDEN, "no more getter")
    var pOld_getter_deprecatedOnGetter: String? = null
        get() = field ?: "null?"

    @set:Deprecated(level = DeprecationLevel.HIDDEN, "no more setter")
    var pOld_getter_deprecatedOnSetter: String? = null
        get() = field ?: "null?"

    var pNew_getter: String? = null
        get() = field ?: "null?"
}