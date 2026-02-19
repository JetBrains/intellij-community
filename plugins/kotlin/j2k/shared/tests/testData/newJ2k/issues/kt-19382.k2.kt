package test

class TestPrimitiveFromMap {
    fun foo(map: HashMap<String?, Int?>): Int {
        return map.get("zzz")!!
    }
}
