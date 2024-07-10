// ERROR: Variable 'name' must be initialized
// INCLUDE_J2K_POSTPROCESSOR_EXTENSIONS
class Main(bar: String?) {
    var name: String
    private var mCount = 0

    init {
        this.name = name
    }

    fun increment() {
        mCount++
    }

    companion object {
        @JvmStatic
        fun main(a: Array<String>) {
            println("Hello world!")
        }

        fun doThing(i: Int) {
            throw RuntimeException("oops")
        }
    }
}
