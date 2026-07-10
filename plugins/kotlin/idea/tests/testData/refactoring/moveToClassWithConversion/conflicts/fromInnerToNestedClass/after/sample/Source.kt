package sample

class Source {
    val v: Int = 1

    class Nested {
        fun foo(inner: Inner) {
            println(v)
            println(this)
        }
    }

    inner class Inner {
    }
}

