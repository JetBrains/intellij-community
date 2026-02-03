package test

open class JavaClass {
    @JvmField
    var field: Int = 0

    var property: Int = 0
        protected set

    companion object {
        const val NAME: String = "Bar"
    }
}
