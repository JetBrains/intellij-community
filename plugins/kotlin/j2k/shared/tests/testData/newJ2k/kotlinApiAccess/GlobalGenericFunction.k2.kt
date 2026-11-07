import kotlinApi.globalGenericFunction

internal class C {
    fun foo() {
        val v: Int = globalGenericFunction<Int?>(1)!!
    }
}
