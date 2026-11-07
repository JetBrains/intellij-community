package test

class TestMapGetAsReceiver {
    fun foo(map: MutableMap<String?, String?>): Int {
        return map.get("zzz")!!.length
    }
}
