class FieldNameConstantsHandrolled1 {
  int field1;
  int alsoAField;
  int thirdField;
  public enum TypeTest {
    alsoAField, thirdField, field1;
  }
}
class FieldNameConstantsHandrolled2 {
  int field1;
  int alsoAField;
  int thirdField;
  public enum TypeTest {
    alsoAField, thirdField, field1;
    public String foo() {
      return name();
    }
  }
}

class FieldNameConstantsHandrolled3 {
  int field1;
  int alsoAField;
  int thirdField;
  static class Fields {
    public static final java.lang.String field1 = "field1";
    public static final java.lang.String thirdField = "thirdField";
    public static final int alsoAField = 5;
  }
}
