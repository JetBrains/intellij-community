public class ReplaceGetterFromMethod {
  private int field;

  public int get<caret>Field() {
    return (field);
  }
}
