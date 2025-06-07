// ERROR: Return type mismatch: expected 'MutableCollection<String>', actual 'MutableList<String?>'.
// ERROR: Initializer type mismatch: expected 'MutableCollection<String>', actual 'MutableList<String?>'.
// ERROR: Initializer type mismatch: expected 'MutableSet<String>', actual 'MutableSet<String?>'.
// TODO handle the case when type argument is used in the method return type (make it not-null)
class J {
    var notNullSet: MutableSet<String> = mutableSetOf<String?>()

    var notNullCollection: MutableCollection<String> = mutableListOf<String?>()
    var nullableCollection: MutableCollection<String?> = mutableListOf<String?>()

    var notNullList: MutableList<String> = mutableListOf<String>()
    var nullableList: MutableList<String?> = mutableListOf<String?>()

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

        takeNotNullCollection(mutableListOf<String>())
        takeNotNullCollection(returnNotNullCollection())
    }

    private fun takeNotNullCollection(strings: MutableCollection<String>?) {
    }

    fun returnNotNullCollection(): MutableCollection<String> {
        return mutableListOf<String?>()
    }
}
