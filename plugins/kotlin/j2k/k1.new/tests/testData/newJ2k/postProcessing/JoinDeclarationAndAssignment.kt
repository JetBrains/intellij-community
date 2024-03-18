import java.util.Arrays

class TestCodeInConstructor(val height: Int, val width: Int, initialValue: Int) {
    val size: Int = height * width
    private val data = IntArray(size)

    init {
        Arrays.fill(data, initialValue)
    }

    fun getData(i: Int): Int {
        return data[i]
    }

    fun putData(i: Int, value: Int) {
        data[i] = value
    }
}
