package one

open class KotlinOnlyClass {
  companion object {
    val companionproperty: Int = 4

    @JvmField
    val companionfieldProperty: Int = 4

    @JvmStatic
    val companionstaticProperty: Int = 4

    const val companionconstProperty: Int = 4

    var companionvariable: Int? = 4

    @JvmField
    var companionfieldVariable: Int = 4

    @JvmStatic
    var companionstaticVariable: Int = 4

    lateinit var companionlateinitVariable: Custom

    @JvmStatic
    lateinit var companionlateinitStaticVariable: Custom

    fun companionsimpleFunction(i: Int) {}

    @JvmStatic
    fun companionstaticFunction(i: Int) {
    }

    fun String.companionextension() {}

    @JvmStatic
    fun String.companionstaticExtension() {}
  }
}

class Custom

open class Proxy : KotlinOnlyClass()