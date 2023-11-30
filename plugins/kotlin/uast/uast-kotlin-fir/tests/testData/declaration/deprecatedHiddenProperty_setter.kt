package test.pkg

class Test {
    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
    var pOld_setter_deprecatedOnProperty: String? = null
        set(value) {
            if (field == null) {
                field = value
            }
        }

    @get:Deprecated(level = DeprecationLevel.HIDDEN, "no more getter")
    var pOld_setter_deprecatedOnGetter: String? = null
        set(value) {
            if (field == null) {
                field = value
            }
        }

    @set:Deprecated(level = DeprecationLevel.HIDDEN, "no more setter")
    var pOld_setter_deprecatedOnSetter: String? = null
        set(value) {
            if (field == null) {
                field = value
            }
        }

    var pNew_setter: String? = null
        set(value) {
            if (field == null) {
                field = value
            }
        }
}