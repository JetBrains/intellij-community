import java.util.Optional

class J {
    private val field: Optional<String>? = null

    fun returnOptional(): Optional<String> {
        return field!!
    }

    fun paramOptional(os: Optional<String>) {
    }

    fun nullableOptional(): Optional<String>? {
        return null
    }
}
