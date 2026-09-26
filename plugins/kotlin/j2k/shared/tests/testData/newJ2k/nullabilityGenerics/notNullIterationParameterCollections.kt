internal class TestCollection {
    var stringsField: MutableCollection<String> = ArrayList<String>()

    fun field() {
        for (s in stringsField) {
            println(s.length)
        }
    }

    fun param(strings: MutableCollection<String>) {
        for (s in strings) {
            println(s.length)
        }
    }

    fun local() {
        val stringsLocal: MutableCollection<String> = ArrayList<String>()
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

    fun param(strings: MutableList<String>) {
        for (s in strings) {
            println(s.length)
        }
    }

    fun local() {
        val stringsLocal: MutableList<String> = ArrayList<String>()
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

    fun param(strings: MutableSet<String>) {
        for (s in strings) {
            println(s.length)
        }
    }

    fun local() {
        val stringsLocal: MutableSet<String> = HashSet<String>()
        for (s in stringsLocal) {
            println(s.length)
        }
    }
}
