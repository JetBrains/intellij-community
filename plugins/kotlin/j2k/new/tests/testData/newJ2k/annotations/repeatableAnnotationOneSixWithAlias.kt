@JvmRepeatable(AContainer::class)
internal annotation class A(val value: Int)
internal annotation class AContainer(vararg val value: A)
