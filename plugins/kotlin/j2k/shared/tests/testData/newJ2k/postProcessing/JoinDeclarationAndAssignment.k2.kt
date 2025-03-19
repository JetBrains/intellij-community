import java.util.Arrays

class TestCodeInConstructor(val height: Int, val width: Int, initialValue: Int) {
    val size: Int
    private val data: IntArray

    init {
        this.size = height * width
        this.data = IntArray(size)
        Arrays.fill(data, initialValue)
    }

    fun getData(i: Int): Int {
        return data[i]
    }

    fun putData(i: Int, value: Int) {
        data[i] = value
    }
}
