package b

import a.Param
import kotlin.collections.plusAssign

fun foo() {

    val p = mutableListOf<Param>()

    fun bar() {
        p += Param("a")
    }
}