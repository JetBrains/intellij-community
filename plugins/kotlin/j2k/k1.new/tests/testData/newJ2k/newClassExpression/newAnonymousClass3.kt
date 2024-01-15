import kotlinApi.KotlinInterface

internal class C {
    fun foo() {
        val t: KotlinInterface = object : KotlinInterface {
            override fun nullableFun(): String? {
                return null
            }

            override fun notNullableFun(): String {
                return ""
            }

            override fun nonAbstractFun(): Int {
                return 0
            }
        }
    }
}