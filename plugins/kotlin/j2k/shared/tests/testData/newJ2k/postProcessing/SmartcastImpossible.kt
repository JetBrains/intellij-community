class C {
    private var x = ""
    var other: C? = null

    fun test(c: C) {
        if (c.other != null) {
            c.other!!.x = ""
        }
    }
}
