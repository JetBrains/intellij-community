class Temp1 {
    private val listField: MutableList<Int?>

    init {
        listField = ArrayList<Int?>()
    }

    fun m() {
        listField.add(2)
        listField.add(1)
        for (i in listField) {
            println(i)
        }
    }
}
