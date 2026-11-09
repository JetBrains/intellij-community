internal class A {
    private val read: List<String?> = ArrayList<String?>()
    private val changed: MutableList<String> = ArrayList<String>()
    val changedThroughGetter: MutableList<String> = ArrayList<String>()
    var packageRead: List<String?> = ArrayList<String?>()
    var publicRead: MutableList<String?> = ArrayList<String?>()

    fun size(): Int {
        return read.size + packageRead.size + publicRead.size
    }

    fun change() {
        changed.add("a")
    }

    fun changeThroughGetter() {
        this.changedThroughGetter.add("a")
    }
}
