import lombok.Data;

/**
 * Test class for renaming fields in a class with @Data annotation
 */
@Data
public class RenameFieldInDataClass {
  private String oldField;
  private int count;

  public void printInfo() {
    System.out.println("Field value: " + getOldField() + ", Count: " + getCount());

    // Direct field access
    System.out.println("Direct access - Field: " + oldField);
  }

  public void updateField(String newValue) {
    setOldField(newValue);
  }
}