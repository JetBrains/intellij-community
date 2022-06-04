abstract class AbstractBaseWithAbstractEquals {
    abstract override fun equals(other: Any?): Boolean
}

data class Data1(val a: Int) : AbstractBaseWithAbstractEquals()

abstract class AbstractBaseWithImplementedEquals {
    override fun equals(other: Any?): Boolean = true
}

data class Data2(val a: Int) : AbstractBaseWithImplementedEquals()

abstract class AbstractBaseWithFinalEquals {
    final override fun equals(other: Any?): Boolean = true
}

data class Data3(val a: Int) : AbstractBaseWithFinalEquals()

abstract class AbstractBaseWithNoEquals

data class Data4(val a: Int) : AbstractBaseWithNoEquals()

data class Data5(val a: Int) : AbstractBaseWithNoEquals() {
    // explicit equals definition
    override fun equals(other: Any?): Boolean = true
}