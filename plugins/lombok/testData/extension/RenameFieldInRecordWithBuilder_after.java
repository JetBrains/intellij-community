import lombok.Builder;

/**
 * Test record for renaming fields in a record with @Builder annotation
 */
@Builder
public record RenameFieldInRecordWithBuilder(
    String newField,
    int count
) {
  public void printInfo() {
    System.out.println("Field value: " + newField() + ", Count: " + count());
  }

  public static void main(String[] args) {
    // Test builder methods
    RenameFieldInRecordWithBuilder record = RenameFieldInRecordWithBuilder.builder()
        .newField("test value")
        .count(42)
        .build();

    System.out.println("Field value: " + record.newField());

    // Create another instance with different values
    RenameFieldInRecordWithBuilder.builder()
        .newField("another value")
        .count(100)
        .build();
  }
}