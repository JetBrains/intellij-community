import lombok.Builder;
import lombok.Data;

/**
 * Test class for renaming fields in a class with both @Data and @Builder annotations
 */
@Data
@Builder
public class RenameFieldInDataBuilderClass {
  private String oldField;
  private int count;

  public void printInfo() {
    System.out.println("Field value: " + getOldField() + ", Count: " + getCount());
  }

  public static void main(String[] args) {
    // Test builder methods
    RenameFieldInDataBuilderClass instance = RenameFieldInDataBuilderClass.builder()
        .oldField("test value")
        .count(42)
        .build();

    System.out.println("Field value: " + instance.getOldField());

    // Test setter methods
    instance.setOldField("updated value");
    System.out.println("Updated field value: " + instance.getOldField());
  }
}