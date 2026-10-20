class Example {
    companion {
        fun /*rename*/fromBlock(value: String): String = value
    }
}

companion fun Example.fromExtension(value: Int): Int = value

fun fromBlockOutside(): String = Example.fromBlock("block")

fun fromExtensionOutside(): Int = Example.fromExtension(1)
