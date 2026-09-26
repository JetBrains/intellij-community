package fields

class ClassWithCompanion {
    val foo: String = "1"

    companion object {
        val foo: Int = 0
    }
}

class ClassJvmField {
    @JvmField
    val foo: String = "1"

    companion object {
        val foo: Int = 0
    }
}

class CompanionJvmField {
    val foo: String = "1"

    companion object {
        @JvmField
        val foo: Int = 0
    }
}

class ClassAndCompanionJvmField {
    @JvmField
    val foo: String = "1"

    companion object {
        @JvmField
        val foo: Int = 0
    }
}

object ObjectWithProperties {
    val property: Int = 0

    @JvmField
    val propertyWithJvmField: String = "str"
}

val topLevelProperty: Int = 0

@JvmField
val topLevelPropertyWithJvmField: String = "str"