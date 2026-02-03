internal class A {
    private var i = returnByte().toInt()

    fun foo() {
        i = 10
    }

    companion object {
        fun returnByte(): Byte {
            return 0
        }
    }
}
