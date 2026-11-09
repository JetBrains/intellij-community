class J {
    fun foo(e0: MutableMap.MutableEntry<Int?, String>, e1: Map.Entry<Int?, String?>) {
        e0.setValue("nya")
        e1.value
    }
}
