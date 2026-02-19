import kotlin.math.max
import kotlin.math.pow

class JJ {
    fun foo() {
        var sum = 0

        sum = (sum + 1.0.pow(2.0)).toInt()
        sum = (sum + 1.0.pow(2.0)).toInt()
        sum += max(1, 2)
        sum = (sum + max(1.0, 2.0)).toInt()
    }
}
