class J(private var s1: String?) {
    private var s2: String? = null
    private var s3: String? = null

    constructor(s1: String?, s2: String?) : this(s1) {
        this.s2 = s2
    }

    constructor(s1: String?, s2: String?, s3: String?) : this(s1, s2) {
        this.s3 = s3
    }
}
