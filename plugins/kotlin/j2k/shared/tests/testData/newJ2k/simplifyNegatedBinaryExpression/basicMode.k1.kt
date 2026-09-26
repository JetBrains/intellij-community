// !BASIC_MODE: true
internal class C {
    fun foo(a: Int, b: Int, s1: String, s2: String) {
        if (!(0 != 1)) return
        if (!(s1 === s2)) return
        if (!(a > b)) return
    }
}
