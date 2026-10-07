// !ADD_JPA_ANNOTATIONS
// KTIJ-39455: a JPA provider writes a mapped field after construction, so `val` fails at runtime
@jakarta.persistence.Entity
public class J {
    @javax.persistence.Id
    @javax.persistence.GeneratedValue
    private Long id;

    @jakarta.persistence.Embedded
    private String address;

    @jakarta.persistence.Version
    private Long version;

    @jakarta.persistence.OneToMany
    private java.util.List<String> children;

    @jakarta.persistence.Transient
    private String notMapped;
}
