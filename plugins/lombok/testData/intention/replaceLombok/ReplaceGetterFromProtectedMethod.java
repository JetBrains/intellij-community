public class ReplaceGetterFromProtectedMethod {
  private int field;

  protected int get<caret>Field() {
    return (field);
  }
}
