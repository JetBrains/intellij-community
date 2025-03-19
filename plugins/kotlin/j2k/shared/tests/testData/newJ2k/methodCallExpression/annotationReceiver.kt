internal annotation class Ann(val foobar: String)

internal class C {
    fun process(annotation: Ann) {
        println(annotation.toString())
        println(annotation.foobar)
    }
}
