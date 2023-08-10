// EXTRACTION_TARGET: property with getter
// WITH_STDLIB

import java.util.*

// SIBLING:
class Foo<T> {
    val map = HashMap<String, T>()

    fun test(): T {
        return <selection>map[""]</selection>
    }
}