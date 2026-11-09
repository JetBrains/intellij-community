internal class A {
    var fieldChangedInKotlin: MutableList<String?> = ArrayList<String?>()
    @JvmField
    var fieldReadInJava: List<String?> = ArrayList<String?>()

    fun changedInKotlin(): MutableList<String?> {
        return ArrayList<String?>()
    }

    fun readInJava(): List<String?> {
        return ArrayList<String?>()
    }
}
