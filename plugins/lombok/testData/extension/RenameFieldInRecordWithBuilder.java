import lombok.Builder;

/**
 * Test record for renaming fields in a record with @Builder annotation
 */
@Builder
public record RenameFieldInRecordWithBuilder(
    String oldField,
    int count
) {
  public void printInfo() {
    System.out.println("Field value: " + oldField() + ", Count: " + count());
  }

  public static void main(String[] args) {
    // Test builder methods
    RenameFieldInRecordWithBuilder record = RenameFieldInRecordWithBuilder.builder()
        .oldField("test value")
        .count(42)
        .build();

    System.out.println("Field value: " + record.oldField());

    // Create another instance with different values
    RenameFieldInRecordWithBuilder.builder()
        .oldField("another value")
        .count(100)
        .build();
  }
}