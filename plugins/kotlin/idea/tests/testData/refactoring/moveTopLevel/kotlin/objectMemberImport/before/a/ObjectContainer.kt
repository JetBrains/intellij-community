package a

import a.NestedObject.CONST
import a.NestedObject.objectFun

object NestedObject {
    const val CONST = 0
    fun objectFun() {}
}

fun re<caret>fer() {
    CONST
    objectFun()
}