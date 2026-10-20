class Example {
    companion {
        fun fromBlock(value: String): String = value
    }
}

companion fun Example./*rename*/fromExtension(value: Int): Int = value

fun fromOutside(): Int = Example.fromExtension(1)
