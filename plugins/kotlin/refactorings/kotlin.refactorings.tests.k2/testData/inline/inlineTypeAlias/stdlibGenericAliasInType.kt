// ASSERT_LIBRARY_ALIAS_SURVIVES

import java.util.ArrayList

fun main() {
    val list: kotlin.collections.<caret>ArrayList<String> = ArrayList()
    println(list)
}
