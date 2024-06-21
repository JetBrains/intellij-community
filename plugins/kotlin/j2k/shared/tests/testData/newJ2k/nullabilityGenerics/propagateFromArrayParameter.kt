class C {
    fun foo(strings: Array<String>) {
        bar(strings)
    }

    fun bar(strings: Array<String>) {
        baz(strings)
    }

    fun baz(strings: Array<String>) {
        for (s in strings) {
            println(s.length)
        }
    }
}
