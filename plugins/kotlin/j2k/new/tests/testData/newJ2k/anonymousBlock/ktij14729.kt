class Temp1 {
    private val listField: MutableList<Int> = ArrayList()

    fun m() {
        listField.add(2)
        listField.add(1)
        for (i in listField) {
            println(i)
        }
    }
}
