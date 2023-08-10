public class ReplaceSetterFromProtectedMethod {
  private int field;

  protected void set<caret>Field(int field) {
    this.field = field;
  }
}
