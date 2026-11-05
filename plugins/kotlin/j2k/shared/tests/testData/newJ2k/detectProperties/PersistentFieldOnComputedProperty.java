// !ADD_JPA_ANNOTATIONS
// JPA writes a persistent field, so the property keeps its backing field when the getter does not read it
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Event {
    @Id
    private Long id;

    private String name;

    private String label;

    public String getLabel() {
        return "Label: " + name;
    }
}
