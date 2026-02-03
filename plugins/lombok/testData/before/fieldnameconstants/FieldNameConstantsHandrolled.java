import lombok.experimental.FieldNameConstants;
import lombok.AccessLevel;

@FieldNameConstants(asEnum = true, innerTypeName = "TypeTest")
class FieldNameConstantsHandrolled1 {
  int field1, alsoAField, thirdField;

  public enum TypeTest {
    field1
  }
}

@FieldNameConstants(asEnum = true, innerTypeName = "TypeTest")
class FieldNameConstantsHandrolled2 {
  int field1, alsoAField, thirdField;

  public enum TypeTest {
    field1;

    public String foo() {
      return name();
    }
  }
}

@FieldNameConstants
class FieldNameConstantsHandrolled3 {
  int field1, alsoAField, thirdField;

  static class Fields {
    public static final int alsoAField = 5;
  }
}
