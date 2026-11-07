// ERROR: 'getSomething1' overrides nothing.
// ERROR: 'getSomething2' overrides nothing.
// ERROR: 'getSomething3' overrides nothing.
// ERROR: 'setSomething3' overrides nothing.
package test

abstract class C internal constructor(private val mySomething1: Int) : B(), I {
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

    fun getFromB5(): String {
        return ""
    }

    override fun setFromB5(value: String?) {
        super.setFromB5(value)
    }
}
