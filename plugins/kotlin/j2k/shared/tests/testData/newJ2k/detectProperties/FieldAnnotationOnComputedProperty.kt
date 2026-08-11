// ERROR: Property must be initialized.
import jakarta.persistence.Column

class Entity(private val location: String) {
    @get:Column
    var summary: String?
        get() = "Summary for " + location
        private set
}
