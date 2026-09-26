// INCLUDE_J2K_PREPROCESSOR_EXTENSIONS
class Main {
    private var mCount = 0
    fun prebar() {
        mCount++
    }

    companion object {
        fun doThing(i: Int) {
            throw RuntimeException("oops")
        }
    }
}