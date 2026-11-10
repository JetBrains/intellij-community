internal class A {
    fun take(vararg lists: MutableList<String?>?) {
    }

    fun passLocal() {
        val list: MutableList<String?> = ArrayList<String?>()
        take(list)
    }

    fun passParameter(list: MutableList<String?>?) {
        take(list, list)
    }
}
