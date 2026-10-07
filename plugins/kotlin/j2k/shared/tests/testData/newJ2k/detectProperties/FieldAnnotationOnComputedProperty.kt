import jakarta.persistence.Column

class Entity(private val location: String?) {
    @get:Column
    val summary: String?
        get() = "Summary for " + location
}
