package foo

object MainObject {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Hi ${MainObject::class.java.name}")
    }
}
