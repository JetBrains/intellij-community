import lombok.With;

/**
 * Test record for renaming fields in a record with Lombok annotations
 */
public record RenameFieldInRecord(
    @With String oldField,
    int count
) {
  public void printInfo() {
    System.out.println("Field value: " + oldField() + ", Count: " + count());
  }

  public static void main(String[] args) {
    RenameFieldInRecord record = new RenameFieldInRecord("test value", 42);
    System.out.println("Field value: " + record.oldField());

    // Test wither method
    RenameFieldInRecord updatedRecord = record.withOldField("updated value");
    System.out.println("Updated field value: " + updatedRecord.oldField());
  }
}