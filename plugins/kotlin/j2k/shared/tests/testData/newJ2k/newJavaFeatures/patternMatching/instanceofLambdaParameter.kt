import java.util.function.Consumer

class Example {
    fun test(objects: ArrayList<Any?>) {
        objects.forEach(Consumer { o: Any? ->
            if (o is String) {
                println(o)
            }
        })
    }
}
