class A {
    companion object {
        val String.p: String
            get() = ""
    }

    fun test() {
        val test = "".p
    }
}