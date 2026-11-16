// ERROR: NO_VALUE_FOR_PARAMETER
import kotlinApi.KotlinClass

internal class C : KotlinClass() {
    fun foo() {
        println(property)
        property = "a"
    }
}
