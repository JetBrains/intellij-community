package test.pkg

class Test {
    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
    var pOld_accessors_deprecatedOnProperty: String? = null
        get() = field ?: "null?"
        set(value) {
            if (field == null) {
                field = value
            }
        }

    @get:Deprecated(level = DeprecationLevel.HIDDEN, "no more getter")
    var pOld_accessors_deprecatedOnGetter: String? = null
        get() = field ?: "null?"
        set(value) {
            if (field == null) {
                field = value
            }
        }

    @set:Deprecated(level = DeprecationLevel.HIDDEN, "no more setter")
    var pOld_accessors_deprecatedOnSetter: String? = null
        get() = field ?: "null?"
        set(value) {
            if (field == null) {
                field = value
            }
        }

    var pNew_accessors: String? = null
        get() = field ?: "null?"
        set(value) {
            if (field == null) {
                field = value
            }
        }
}