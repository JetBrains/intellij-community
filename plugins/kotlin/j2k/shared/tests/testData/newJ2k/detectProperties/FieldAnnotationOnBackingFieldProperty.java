// ADD_JPA_ANNOTATIONS
import jakarta.persistence.Column;

public class Entity {
    @Column
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
