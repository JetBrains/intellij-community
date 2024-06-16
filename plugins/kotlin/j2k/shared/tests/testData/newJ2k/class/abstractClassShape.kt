internal abstract class Shape {
    var color: String? = null
    fun sColor(c: String?) {
        color = c
    }

    fun gColor(): String? {
        return color
    }

    abstract fun area(): Double
}
