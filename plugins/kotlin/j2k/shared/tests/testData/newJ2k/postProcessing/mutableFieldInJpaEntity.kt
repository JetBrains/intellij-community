import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.Transient

@Entity
class Person {
    private var id: Long? = null
    private var name: String? = null

    @Transient
    private val cache: String? = null

    private var helper: Helper? = null

    companion object {
        private val shared: String? = null
    }
}

@Embeddable
internal class Address(private var city: String?)

internal class Helper {
    private val plain: String? = null
}
