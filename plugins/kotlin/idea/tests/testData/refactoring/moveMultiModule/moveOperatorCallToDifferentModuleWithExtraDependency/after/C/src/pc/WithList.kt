package pc

import pa.Bar
import kotlin.collections.plus

class WithList {
    var list: List<Bar> = emptyList()

    fun foo() {
        list += Bar()
    }
}