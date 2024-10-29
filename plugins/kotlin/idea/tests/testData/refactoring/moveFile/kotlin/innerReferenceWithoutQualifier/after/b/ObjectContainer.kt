package b

import b.ObjectContainer.NestedObject.CONST
import b.ObjectContainer.NestedObject.objectFun

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