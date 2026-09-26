package a

import a.ObjectContainer.NestedObject.CONST
import a.ObjectContainer.NestedObject.objectFun

class ObjectContainer {
    object NestedObject {
        const val CONST = 0
        fun objectFun() {}
    }

    fun refer() {
        CONST
        objectFun()
    }
}