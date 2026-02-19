class C {
    var x: String? = "old"
        get() {
            println("side effect")
            return field
        }
        set(x) {
            println("old value: " + field)
            field = x
        }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val c = C()
            c.x = "new"
        }
    }
}
