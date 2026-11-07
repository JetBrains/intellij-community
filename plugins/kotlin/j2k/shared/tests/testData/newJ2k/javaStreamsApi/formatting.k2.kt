// ERROR: Type argument is not within its bounds: type parameter 'T (of fun <T : Comparable<in T!>!> naturalOrder)' must be subtype of 'Comparable<in Int?>!', but actual: 'Int?'.
import java.util.stream.Collectors

internal class Test {
    fun main(lst: MutableList<Int?>) {
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
