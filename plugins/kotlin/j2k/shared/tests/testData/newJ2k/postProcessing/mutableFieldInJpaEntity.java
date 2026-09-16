// !ADD_JPA_ANNOTATIONS
// KTIJ-39455: JPA writes every persistent field after construction, so `val` fails at runtime
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class Person {
    private Long id;
    private String name;

    @Transient
    private String cache;

    private static String shared;

    private Helper helper;
}

@Embeddable
class Address {
    private final String city;

    Address(String city) {
        this.city = city;
    }
}

class Helper {
    private String plain;
}
