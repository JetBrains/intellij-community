import jakarta.persistence.Embedded
import jakarta.persistence.OneToMany
import jakarta.persistence.Version
import javax.persistence.GeneratedValue
import javax.persistence.Id

// !ADD_JPA_ANNOTATIONS
// KTIJ-39455: a JPA provider writes a mapped field after construction, so `val` fails at runtime
class J {
    @Id
    @GeneratedValue
    private var id: Long? = null

    @Embedded
    private var address: String? = null

    @Version
    private var version: Long? = null

    @OneToMany
    private var children: MutableList<String?>? = null

    private val notMapped: String? = null
}
