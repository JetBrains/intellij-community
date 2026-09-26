package test.pkg

class Test(
    @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
    var pOld_deprecatedOnProperty: Int,

    // Not applicable
    // @field:Deprecated("no more field", level = DeprecationLevel.HIDDEN)
    // var pOld_deprecatedOnField: Int,

    @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
    var pOld_deprecatedOnGetter: Int,

    // Not applicable
    // @param:Deprecated("no more param", level = DeprecationLevel.HIDDEN)
    // var pOld_deprecatedOnParameter: Int,

    var pNew: Int,
)