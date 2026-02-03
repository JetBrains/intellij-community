// EXTRACTION_TARGET: property with initializer
// WITH_STDLIB

import java.util.*

class Foo<T> {
    val map = HashMap<String, T>()

    fun test(): T {
        return <selection>map[""]</selection>
    }
}