// ERROR: Unresolved label.
import java.util.function.BiFunction

class Test {
    fun notNullParameters(f: BiFunction<Int?, Int?, String>) {
    }

    fun nullableParameter(f: BiFunction<Int?, Int?, String>) {
    }

    fun nullableReturnType(f: BiFunction<Int?, Int?, String?>) {
    }

    fun test() {
        notNullParameters(BiFunction { i: Int?, j: Int? -> if (i!! < 10 && j!! > 0) "" else "" })
        nullableParameter(BiFunction { i: Int?, j: Int? -> if (i == null) "" else "" })
        nullableReturnType(BiFunction { i: Int?, j: Int? ->
            if (i!! < 10) return@nullableReturnType null
            "nya"
        })
    }
}
