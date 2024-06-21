internal class TestCollection {
    var stringsField: Collection<String> = ArrayList()

    fun field() {
        for (s in stringsField) {
            println(s.length)
        }
    }

    fun param(strings: Collection<String>) {
        for (s in strings) {
            println(s.length)
        }
    }

    fun local() {
        val stringsLocal: Collection<String> = ArrayList()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}

internal class TestIterable {
    var stringsField: Iterable<String> = ArrayList()

    fun field() {
        for (s in stringsField) {
            println(s.length)
        }
    }

    fun param(strings: Iterable<String>) {
        for (s in strings) {
            println(s.length)
        }
    }

    fun local() {
        val stringsLocal: Iterable<String> = ArrayList()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}

internal class TestList {
    var stringsField: List<String> = ArrayList()

    fun field() {
        for (s in stringsField) {
            println(s.length)
        }
    }

    fun param(strings: List<String>) {
        for (s in strings) {
            println(s.length)
        }
    }

    fun local() {
        val stringsLocal: List<String> = ArrayList()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}

internal class TestSet {
    var stringsField: Set<String> = HashSet()

    fun field() {
        for (s in stringsField) {
            println(s.length)
        }
    }

    fun param(strings: Set<String>) {
        for (s in strings) {
            println(s.length)
        }
    }

    fun local() {
        val stringsLocal: Set<String> = HashSet()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}
