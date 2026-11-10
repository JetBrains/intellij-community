internal class A {
    fun changedInAnonymousClass(items: MutableList<String>) {
        val runnable: Runnable = object : Runnable {
            override fun run() {
                items.add("a")
            }
        }
        runnable.run()
    }

    fun readInAnonymousClass(items: List<String?>) {
        val runnable: Runnable = object : Runnable {
            override fun run() {
                println(items.size)
            }
        }
        runnable.run()
    }

    fun changedInLocalClass(items: MutableList<String>) {
        class Adder {
            fun add() {
                items.add("a")
            }
        }
        Adder().add()
    }
}
