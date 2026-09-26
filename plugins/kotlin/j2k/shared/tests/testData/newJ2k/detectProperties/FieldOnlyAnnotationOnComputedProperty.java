public class FieldOnlyEntity {
    private final String location;

    @FieldOnly
    private String summary;

    public FieldOnlyEntity(String location) {
        this.location = location;
    }

    public String getSummary() {
        return "Summary for " + location;
    }
}
