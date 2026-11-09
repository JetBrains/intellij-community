internal class TestCollection {
    var stringsField: MutableCollection<String> = ArrayList<String>()

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
        val stringsLocal: Collection<String> = ArrayList<String>()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}

internal class TestIterable {
    var stringsField: Iterable<String> = ArrayList<String>()

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
        val stringsLocal: Iterable<String> = ArrayList<String>()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}

internal class TestList {
    var stringsField: MutableList<String> = ArrayList<String>()

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
        val stringsLocal: List<String> = ArrayList<String>()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}

internal class TestSet {
    var stringsField: MutableSet<String> = HashSet<String>()

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
        val stringsLocal: Set<String> = HashSet<String>()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}
