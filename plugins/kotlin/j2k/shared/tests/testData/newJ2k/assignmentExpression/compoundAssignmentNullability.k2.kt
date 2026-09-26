import java.util.function.Consumer

internal class C {
    fun foo(list: ArrayList<Int?>) {
        val result = intArrayOf(0)
        list.forEach(Consumer { integer: Int? -> result[0] += integer!! })
    }
}
