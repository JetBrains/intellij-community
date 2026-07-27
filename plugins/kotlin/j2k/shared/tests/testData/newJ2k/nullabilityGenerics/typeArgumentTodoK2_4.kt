// ERROR: Initializer type mismatch: expected 'ArrayList<String>', actual 'ArrayList<String?>!'.
class Foo {
    fun test() {
        val nullableList = J.nullableList<String?>()
        val notNullList = J.notNullList<String?>()

        val unknownListNullable = J.unknownList<String?>()
        val unknownListNotNull: ArrayList<String> = J.unknownList<String?>()

        val unrelatedListNotNull = J.unrelatedList<Any?>()
        val unrelatedList2 = J.unrelatedList<Any?>()

        for (s in notNullList) {
            println(s.length)
        }

        for (s in unknownListNotNull) {
            println(s.length)
        }

        for (s in unknownListNullable) {
            if (s != null) {
                println(s.length)
            }
        }

        for (s in unrelatedListNotNull) {
            println(s.length)
        }
    }
}
