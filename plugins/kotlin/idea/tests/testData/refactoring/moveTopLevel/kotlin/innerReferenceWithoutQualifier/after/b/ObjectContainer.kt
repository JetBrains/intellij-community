package b

class ObjectContainer {
    object NestedObject {
        const val CONST = 0
        fun objectFun() {}
    }

    fun refer() {
        NestedObject.CONST
        NestedObject.objectFun()
    }
}