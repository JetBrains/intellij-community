import kotlin.math.max

class J {
    fun foo(a: Int, b: Int, l: Long, s: Short) {
        for (i in 0..<max(a.toDouble(), b.toDouble()).toInt()) {
        }

        for (i in 0..max(a.toDouble(), b.toDouble()).toInt()) {
        }

        for (i in 10 downTo max(a.toDouble(), b.toDouble()).toInt()) {
        }


        // OK with integral types
        for (i in 0..<l) {
        }

        for (i in 0..s) {
        }
    }
}
