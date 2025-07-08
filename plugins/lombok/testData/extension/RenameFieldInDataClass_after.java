import lombok.Data;

/**
 * Test class for renaming fields in a class with @Data annotation
 */
@Data
public class RenameFieldInDataClass {
  private String newField;
  private int count;

  public void printInfo() {
    System.out.println("Field value: " + getNewField() + ", Count: " + getCount());

    // Direct field access
    System.out.println("Direct access - Field: " + newField);
  }

  public void updateField(String newValue) {
    setNewField(newValue);
  }
}