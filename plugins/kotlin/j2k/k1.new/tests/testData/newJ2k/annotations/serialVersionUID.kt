import java.io.Serializable

class Bar : Serializable {
    var foobar: Int = 0

    companion object {
        private const val serialVersionUID: Long = 0
    }
}

class Foo {
    var foobar: Int = 0

    companion object {
        private const val serialVersionUID: Long = 0
    }
}
