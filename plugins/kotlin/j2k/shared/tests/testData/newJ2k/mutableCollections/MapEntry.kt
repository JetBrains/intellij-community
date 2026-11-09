class J {
    fun foo(e0: MutableMap.MutableEntry<Int?, String>, e1: MutableMap.MutableEntry<Int?, String?>) {
        e0.setValue("nya")
        e1.value
    }
}
