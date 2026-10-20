class Example {
    companion {
        val fromExtension: String.() -> String = { this }
    }
}

companion fun Example.fromExtension(): String = "extension"

fun fromOutside(): String = Example.fromExtension("value") + Example.fromExtension()
