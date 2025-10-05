import lombok.Builder;
import lombok.Getter;

/**
 * Test class for renaming fields in a class with @Builder annotation
 */
@Builder
@Getter
public class RenameFieldInBuilderClass {
  private final String oldField;
  private final int count;

  public static void main(String[] args) {
    // Test builder methods
    RenameFieldInBuilderClass instance = RenameFieldInBuilderClass.builder()
        .oldField("test value")
        .count(42)
        .build();

    System.out.println("Field value: " + instance.getOldField());

    // Create another instance with different values
    RenameFieldInBuilderClass.builder()
        .oldField("another value")
        .count(100)
        .build();
  }
}