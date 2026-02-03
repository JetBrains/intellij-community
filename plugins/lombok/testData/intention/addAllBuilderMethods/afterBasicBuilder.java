import lombok.Builder;

@Builder
public class BasicBuilder {
    private String name;
    private Integer age;
    private Boolean active;

    public static void main(String[] args) {
        BasicBuilder.builder().name().age().active().build();
    }
}