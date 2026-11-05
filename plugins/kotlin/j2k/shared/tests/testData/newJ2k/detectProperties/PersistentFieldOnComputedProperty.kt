import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class Event {
    @Id
    private var id: Long? = null

    private var name: String? = null

    var label: String? = null
        get() = "Label: " + name
        private set
}
