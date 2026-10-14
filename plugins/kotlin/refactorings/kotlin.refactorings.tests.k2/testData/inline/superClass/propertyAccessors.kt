open class <caret>Base {
    val computed: Int
        get() = 42

    var mutable: String = ""
        private set

    lateinit var late: String
}

class Child : Base()
