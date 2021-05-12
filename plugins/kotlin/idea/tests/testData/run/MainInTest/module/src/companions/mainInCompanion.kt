package companions

// NO-DUMB-MODE
class A0 {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // yes
        }
    }
}

class B0 {
    companion object {
        fun main(args: Array<String>) {
            // no
        }
    }
}

class C0 {
    companion object {
        @JvmStatic
        @JvmName("main0")
        fun main(args: Array<String>) { // no
        }
    }
}

class D0 {
    companion object {
        @JvmStatic
        @JvmName("main")
        fun badName(args: Array<String>) { // yes
        }
    }
}
