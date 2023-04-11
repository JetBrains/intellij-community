import javax.persistence.Column

class J {
    @Column
    private var title: String? = null

    @jakarta.persistence.Column
    private var title2: String? = null
}
