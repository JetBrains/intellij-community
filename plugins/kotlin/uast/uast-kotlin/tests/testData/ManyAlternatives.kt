class ClassA(justParam: Int, val paramAndProp: String) {
    var writebleProp: Int = 0

    companion object {
        @JvmStatic
        fun foo() { }
    }
}
