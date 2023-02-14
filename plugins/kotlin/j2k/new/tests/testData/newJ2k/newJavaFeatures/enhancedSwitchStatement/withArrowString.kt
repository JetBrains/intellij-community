internal class MyClass {
    fun method(str: String?) {
        when (str) {
            "1", "2" -> println(12)
            "3" -> println(3)
            else -> println(4)
        }
    }
}
