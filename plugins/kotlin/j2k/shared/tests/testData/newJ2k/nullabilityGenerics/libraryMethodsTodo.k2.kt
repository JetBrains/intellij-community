class J {
    var notNullSet: Set<String> = emptySet<String>()

    var notNullCollection: Collection<String> = emptyList<String>()
    var nullableCollection: Collection<String?> = emptyList<String?>()

    var notNullList: List<String> = listOf<String>()
    var nullableList: List<String?> = listOf<String?>()

    fun foo() {
        for (s in notNullSet) {
            println(s.length)
        }
        for (s in notNullCollection) {
            println(s.length)
        }
        for (s in nullableCollection) {
            if (s != null) {
                println(s.length)
            }
        }

        takeNotNullCollection(emptyList<String>())
        takeNotNullCollection(returnNotNullCollection())
    }

    private fun takeNotNullCollection(strings: Collection<String>) {
    }

    fun returnNotNullCollection(): Collection<String> {
        return emptyList<String>()
    }
}
