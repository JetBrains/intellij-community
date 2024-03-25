internal class Outer {
    enum class EnumClass {
        A;

        val mavenIds: Array<String>

        init {
            mavenIds = arrayOf("da", "da")
        }
    }
}
class Foo {
    private fun createModule() {
        <selection>for (mvnId in Outer.EnumClass.A.run { mavenIds }) { }</selection>
    }
}