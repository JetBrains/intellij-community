class Example {
    companion {
        val /*rename*/fromBlock: String.() -> String = { this }
    }
}

companion fun Example.fromExtension(): String = "extension"

fun fromOutside(): String = Example.fromBlock("value") + Example.fromExtension()
