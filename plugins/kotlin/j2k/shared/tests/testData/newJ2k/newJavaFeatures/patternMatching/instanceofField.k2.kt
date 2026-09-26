class Example {
    fun test() {
        if (o is String) {
            println(o)
        }

        if (this.o is String) {
            println(o)
        }
    }

    var o: Any = Any()
}
