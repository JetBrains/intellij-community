package test.pkg

interface TestInterface {
    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
    var pOld_deprecatedOnProperty: Int

    @get:Deprecated(level = DeprecationLevel.HIDDEN, "no more getter")
    var pOld_deprecatedOnGetter: Int

    @set:Deprecated(level = DeprecationLevel.HIDDEN, "no more setter")
    var pOld_deprecatedOnSetter: Int

    var pNew: Int
}