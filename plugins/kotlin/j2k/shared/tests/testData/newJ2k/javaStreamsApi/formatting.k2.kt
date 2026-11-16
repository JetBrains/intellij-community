// ERROR: UPPER_BOUND_VIOLATED
import java.util.stream.Collectors

internal class Test {
    fun main(lst: List<Int?>) {
        val newLst =  /*before list*/lst /*after list*/.stream() /* after stream*/
            .filter { x: Int? -> x!! > 10 }
            .map<Int?> { x: Int? -> x!! + 2 }  /*some comment*/.distinct() /* one more comment */
            .sorted() /*another one comment*/
            .sorted(Comparator.naturalOrder<Int?>())
            .peek { x: Int? -> println(x) }.limit(1)
            .skip(42) /*skipped*/ /*collecting one*/
            .collect(Collectors.toList()) /* cool */
    }
}
