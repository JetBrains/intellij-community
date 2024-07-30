class J(private var s1: String?) {
    private var s2: String? = null

    constructor(s1: String?, s2: String?) : this(s1) {
        this.s2 = s2
    }
}
