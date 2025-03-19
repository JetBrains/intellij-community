import java.util.function.Consumer

class J {
    fun foo(numbers: ArrayList<Int>) {
        numbers.forEach(Consumer { n: Int ->
            var n = n
            n = n + 1
        })
        numbers.forEach(Consumer { n: Int ->
            var n = n
            n = n + 1
        })
    }
}
