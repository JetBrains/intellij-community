package test.pkg

class Test {
    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
    var pOld_noAccessor_deprecatedOnProperty: String = "42"

    @get:Deprecated(level = DeprecationLevel.HIDDEN, "no more getter")
    var pOld_noAccessor_deprecatedOnGetter: String = "42"

    @set:Deprecated(level = DeprecationLevel.HIDDEN, "no more setter")
    var pOld_noAccessor_deprecatedOnSetter: String = "42"

    var pNew_noAccessor: String = "42"
}
