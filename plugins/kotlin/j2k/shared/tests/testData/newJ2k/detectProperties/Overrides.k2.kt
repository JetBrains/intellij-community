// ERROR: 'getFromB1' overrides nothing.
// ERROR: 'getFromB2' overrides nothing.
// ERROR: 'getFromB3' overrides nothing.
// ERROR: 'getSomething1' overrides nothing.
// ERROR: 'getSomething2' overrides nothing.
// ERROR: 'getSomething3' overrides nothing.
// ERROR: 'setFromB2' overrides nothing.
// ERROR: 'setFromB4' overrides nothing.
// ERROR: 'setSomething3' overrides nothing.
internal interface I {
    val something1: Int

    val something2: Int

    var something3: Int

    fun getSomething4(): Int
    fun setSomething4(value: Int)

    fun getSomething5(): Int
    fun setSomething5(value: Int)

    fun setSomething6(value: Int)
}

internal interface I1 : I {
    fun setSomething1(value: Int)

    val something6: Int
}

internal open class B {
    open val fromB1: String
        get() = ""

    open var fromB2: String?
        get() = ""
        set(value) {
        }

    open var fromB3: String?
        get() = ""
        set(value) {
        }

    open var fromB4: String?
        get() = ""
        set(value) {
        }

    open fun setFromB5(value: String?) {
    }
}

internal abstract class C(private val mySomething1: Int) : B(), I {
    private var mySomething6 = 0

    override fun getSomething1(): Int {
        return mySomething1
    }

    override fun getSomething2(): Int {
        return 0
    }

    override fun getSomething3(): Int {
        return 0
    }

    override fun setSomething3(value: Int) {
    }

    override fun getSomething4(): Int {
        return 0
    }

    override fun setSomething5(value: Int) {
    }

    fun getSomething6(): Int {
        return mySomething6
    }

    override fun setSomething6(value: Int) {
        mySomething6 = value
    }

    override fun getFromB1(): String {
        return super.fromB1
    }

    override fun getFromB2(): String {
        return super.fromB2!!
    }

    override fun setFromB2(value: String?) {
        super.fromB2 = value
    }

    override fun getFromB3(): String {
        return super.fromB3!!
    }

    override fun setFromB4(value: String?) {
        super.fromB4 = value
    }

    fun getFromB5(): String {
        return ""
    }

    override fun setFromB5(value: String?) {
        super.setFromB5(value)
    }
}
