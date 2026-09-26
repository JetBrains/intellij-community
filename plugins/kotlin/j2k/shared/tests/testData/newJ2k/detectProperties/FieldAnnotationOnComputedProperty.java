// ADD_JPA_ANNOTATIONS
import jakarta.persistence.Column;

public class Entity {
    private final String location;

    @Column
    private String summary;

    public Entity(String location) {
        this.location = location;
    }

    public String getSummary() {
        return "Summary for " + location;
    }
}
