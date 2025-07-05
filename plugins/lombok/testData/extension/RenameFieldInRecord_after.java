import lombok.With;

/**
 * Test record for renaming fields in a record with Lombok annotations
 */
public record RenameFieldInRecord(
    @With String newField,
    int count
) {
  public void printInfo() {
    System.out.println("Field value: " + newField() + ", Count: " + count());
  }

  public static void main(String[] args) {
    RenameFieldInRecord record = new RenameFieldInRecord("test value", 42);
    System.out.println("Field value: " + record.newField());

    // Test wither method
    RenameFieldInRecord updatedRecord = record.withNewField("updated value");
    System.out.println("Updated field value: " + updatedRecord.newField());
  }
}