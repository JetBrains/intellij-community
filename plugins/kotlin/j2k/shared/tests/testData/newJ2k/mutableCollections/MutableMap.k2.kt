class J {
    fun foo(
        m1: MutableMap<String?, String?>,
        m2: MutableMap<String?, String?>,
        m3: MutableMap<String?, String?>,
        m4: MutableMap<String?, String?>,
        m5: MutableMap<String?, String?>,
        m6: MutableMap<String?, String?>,
        m7: MutableMap<String?, String?>,
        m8: MutableMap<String?, String?>,
        m9: MutableMap<String?, String?>,
        m10: MutableMap<String?, String?>,
        m11: MutableMap<String?, String?>
    ) {
        m1.clear()
        m2.compute("m1") { k: String?, v: String? -> v }
        m3.computeIfAbsent("m2") { k: String? -> "value" }
        m4.computeIfPresent("m1") { k: String?, v: String? -> v }
        m5.merge("", "") { k: String?, v: String? -> v }
        m6.put("m1", "value")
        m7.putAll(m8)
        m8.putIfAbsent("m2", "value")
        m9.remove("")
        m10.replace("m2", "value")
        m11.replaceAll { k: String?, v: String? -> v + "2" }
    }
}
