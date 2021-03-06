public class ReplaceSetterFromMethod {
  private int field;

  public void set<caret>Field(int field) {
    this.field = field;
  }
}
