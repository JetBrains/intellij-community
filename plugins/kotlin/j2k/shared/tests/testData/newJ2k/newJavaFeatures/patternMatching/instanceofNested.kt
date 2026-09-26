import java.io.Serializable

class Example {
    private val field: Any = "hello"

    fun twoLevels(o: Any?) {
        if (o is CharSequence) {
            if (o is String) {
                println(o.length)
            }
        }
    }

    fun threeLevels(o: Any?) {
        if (o is Serializable) {
            if (o is Number) {
                if (o is Int) {
                    println(o + 1)
                }
            }
        }
    }

    fun nestedInSameCondition(o: Any?) {
        if (o is CharSequence && o is String) {
            println(o.length)
        }
    }

    fun compute(): Any {
        return "computed"
    }

    fun methodCallSubject() {
        if (compute() is String) {
            println("matched")
        }
    }

    fun fieldSubject() {
        if (field is CharSequence) {
            if (field is String) {
                println(field.length)
            }
        }
    }
}
