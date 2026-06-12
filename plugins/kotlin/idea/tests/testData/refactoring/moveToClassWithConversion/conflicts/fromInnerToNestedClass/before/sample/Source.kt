package sample

class Source {
    val v: Int = 1

    class Nested

    inner class Inner {
        fun foo<caret>(param: Nested) {
            println(v)
            println(param)
        }
    }
}
