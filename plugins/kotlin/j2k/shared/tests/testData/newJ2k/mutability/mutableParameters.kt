class SomeClass {
    private class Item private constructor(private val module: String, var option: String) {
        constructor(module: String) : this(module, "")
    }

    private val myItems: List<Item> = ArrayList()

    fun setValueAt(aValue: Any, rowIndex: Int, columnIndex: Int) {
        val item = myItems[rowIndex]
        item.option = (aValue as String).trim { it <= ' ' }
    }
}
