// TODO handle the case when type argument is used in the method return type (make it not-null)
class J {
    var notNullSet: Set<String> = emptySet()

    var notNullCollection: Collection<String> = emptyList()
    var nullableCollection: Collection<String> = emptyList()

    var notNullList: List<String> = listOf()
    var nullableList: List<String?> = listOf()

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

        takeNotNullCollection(emptyList())
        takeNotNullCollection(returnNotNullCollection())
    }

    private fun takeNotNullCollection(strings: Collection<String>) {
    }

    fun returnNotNullCollection(): Collection<String> {
        return emptyList()
    }
}
