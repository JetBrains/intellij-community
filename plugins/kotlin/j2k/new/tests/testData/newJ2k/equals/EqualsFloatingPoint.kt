import java.util.Objects

object J {
    @JvmStatic
    fun main(args: Array<String>) {
        val posZero = 0.0f
        val negZero = -0.0
        println(posZero.equals(negZero))
        println(Objects.equals(posZero, negZero))
        println(Objects.equals(posZero, negZero))
        println(posZero.equals(0))
        println(Objects.equals(0, negZero))
        println(0 == 0)
    }
}
