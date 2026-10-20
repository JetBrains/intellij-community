class Example {
    companion {
        fun fromBlock(value: String): String = value
    }
}

companion fun Example.fromBlock(value: Int): Int = value

fun fromOutside(): Int = Example.fromBlock(1)
