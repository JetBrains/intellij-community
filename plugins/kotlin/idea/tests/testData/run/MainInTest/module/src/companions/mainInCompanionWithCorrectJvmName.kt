package companions

class D {
    companion object {
        @JvmStatic
        @JvmName("main")
        fun badName(args: Array<String>) { // yes
        }
    }
}
