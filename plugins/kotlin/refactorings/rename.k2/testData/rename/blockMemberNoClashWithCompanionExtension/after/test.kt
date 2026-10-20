class Example {
    companion {
        fun fromExtension(value: String): String = value
    }
}

companion fun Example.fromExtension(value: Int): Int = value

fun fromBlockOutside(): String = Example.fromExtension("block")

fun fromExtensionOutside(): Int = Example.fromExtension(1)
