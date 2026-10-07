import jakarta.persistence.Entity
import javax.persistence.Column

// !ADD_JPA_ANNOTATIONS
@Entity
class J {
    @Column
    private var title: String? = null

    @jakarta.persistence.Column
    private var title2: String? = null
}
