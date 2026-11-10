internal class A {
    private val readField: MutableList<String> = ArrayList<String>()
    private val changedField: MutableList<String> = ArrayList<String>()
    private val readFirst: List<String?> = ArrayList<String?>()
    private val readSecond: List<String?> = ArrayList<String?>()

    fun locals() {
        val read: MutableList<String> = ArrayList<String>()
        val changed: MutableList<String> = ArrayList<String>()
        changed.add("a")
        println(read)
    }

    fun fields() {
        changedField.add("a")
        println(readField)
        println(readFirst.size + readSecond.size)
    }
}
