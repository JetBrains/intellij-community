class Rectangle {
    var y: Int = 0

    companion object {
        var x: Int = 0
    }
}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val rectangle = Rectangle()
        Rectangle.x = 1
        rectangle.y = 2
    }
}
