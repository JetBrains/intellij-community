package one

interface Interface

class OuterClass {
    class InnerClass {
        fun getInstance(): Interface = null!!
    }
}

fun function() {
    OuterClass.InnerClass().getInstance()
}