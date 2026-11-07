class Outer {
    internal interface Constants {
        companion object {
            val VALUE: Any = "value"
            val TEXT: String = VALUE as String
        }
    }
}
