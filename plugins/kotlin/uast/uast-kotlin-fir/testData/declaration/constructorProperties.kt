package test.pkg

sealed class MyClass(
    val firstConstructorProperty: Int,
    val secondConstructorProperty: Boolean
) {
    val nonConstructorProperty: String = "PROP"
}

data class MyDataClass(
    val constructorProperty: String,
    internal val internalConstructorProperty: String
)