// ERROR: Return type mismatch: expected 'kotlin.collections.MutableCollection<kotlin.String>', actual 'kotlin.collections.MutableList<kotlin.String?>'.
// ERROR: Initializer type mismatch: expected 'kotlin.collections.MutableCollection<kotlin.String>', actual 'kotlin.collections.MutableList<kotlin.String?>'.
// ERROR: Type mismatch: inferred type is 'kotlin.String?', but 'kotlin.String' was expected.
// ERROR: Type mismatch: inferred type is 'kotlin.String?', but 'kotlin.String' was expected.
// ERROR: Initializer type mismatch: expected 'kotlin.collections.MutableSet<kotlin.String>', actual 'kotlin.collections.MutableSet<kotlin.String?>'.
// ERROR: Type mismatch: inferred type is 'kotlin.String?', but 'kotlin.String' was expected.
// ERROR: Type mismatch: inferred type is 'kotlin.String?', but 'kotlin.String' was expected.
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
