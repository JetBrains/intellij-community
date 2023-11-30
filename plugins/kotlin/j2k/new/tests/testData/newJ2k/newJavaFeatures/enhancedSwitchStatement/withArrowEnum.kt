internal enum class ColorEnum {
    GREEN, RED, YELLOW
}

internal class MyClass {
    fun method(colorEnum: ColorEnum?) {
        when (colorEnum) {
            ColorEnum.GREEN, ColorEnum.YELLOW -> println(1)
            else -> println(2)
        }
    }
}
